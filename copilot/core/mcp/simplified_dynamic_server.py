"""
Simplified Dynamic MCP Server implementation for Etendo Copilot.

This module provides a working dynamic MCP server that creates instances on-demand
based on URL path identifiers using FastAPI for better compatibility.
"""

import asyncio
import logging
import socket
import time
from datetime import datetime
from typing import Dict, Optional

import httpx
import uvicorn
from baseutils.logging_envvar import (
    copilot_debug,
    copilot_error,
    copilot_info,
    copilot_warning,
)
from copilot.core.mcp.auth_utils import extract_etendo_token_from_request
from copilot.core.mcp.tools import (
    register_agent_tools,
    register_basic_tools,
)
from copilot.core.threadcontext import ThreadContext
from core.utils.etendo_utils import normalize_etendo_token
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response, StreamingResponse
from fastmcp import FastMCP
from fastmcp.server.auth import BearerAuthProvider

logger = logging.getLogger(__name__)

# Constant for MCP instance time-to-live in minutes
MCP_INSTANCE_TTL_MINUTES = 10


def get_bearer_provider():
    return BearerAuthProvider(
        issuer="sws",
        audience="my-mcp-server",
    )


class DynamicMCPInstance:
    """A single MCP instance that can be created dynamically."""

    def __init__(self, identifier: str, etendo_token: str, server_ref=None):
        self.identifier = identifier
        self.created_at = time.time()
        self.last_activity = time.time()  # For activity tracking
        self.server_ref = server_ref  # Reference to main server
        self.mcp = FastMCP(f"Etendo Copilot Dynamic MCP({identifier})", version=identifier)
        self.port: Optional[int] = None
        self.server_task: Optional[asyncio.Task] = None
        self.uvicorn_server: Optional[uvicorn.Server] = None  # Keep reference to uvicorn server
        self.ttl_task: Optional[asyncio.Task] = None  # Task for TTL management
        self._should_stop = False  # Flag to control server stopping

        # Configure and register tools
        self._setup_tools(identifier, etendo_token)

        copilot_info(f"🔧 Setting up DynamicMCPInstance for identifier: {identifier}")

    def _setup_tools(self, identifier: str, etendo_token: str):
        """Configure tools for this MCP instance."""
        # Register basic tools (identifier extracted from request context)
        register_basic_tools(self.mcp)
        register_agent_tools(self.mcp, identifier, etendo_token)

        copilot_info(f"✅ Tools configured for MCP instance '{self.identifier}'")

    def _find_free_port(self, start_port: int = 5008) -> int:
        """Find a free port starting from start_port, reusing available ports when possible."""
        # First check if server has any available ports to reuse
        if self.server_ref and self.server_ref.available_ports:
            port = self.server_ref.available_ports.pop()
            copilot_debug(f"🔄 Reusing available port {port} for '{self.identifier}'")
            self.server_ref.used_ports.add(port)
            return port

        # Otherwise find a new free port
        for port in range(start_port, start_port + 1000):
            # Skip ports that are currently being used by other instances
            if self.server_ref and port in self.server_ref.used_ports:
                continue

            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                try:
                    s.bind(("localhost", port))
                    if self.server_ref:
                        self.server_ref.used_ports.add(port)
                        copilot_debug(f"🆕 Allocated new port {port} for '{self.identifier}'")
                    return port
                except OSError:
                    continue
        raise RuntimeError("No free ports available")

    def update_activity(self):
        """Updates the last activity timestamp."""
        self.last_activity = time.time()
        copilot_debug(f"⏰ Activity updated for '{self.identifier}' - TTL reset")

    def get_minutes_since_last_activity(self) -> float:
        """Gets the minutes elapsed since last activity."""
        return (time.time() - self.last_activity) / 60.0

    async def _ttl_monitor(self):
        """Monitors TTL and kills server if it exceeds time limit."""
        try:
            copilot_debug(
                f"🕐 TTL monitor started for '{self.identifier}' with {MCP_INSTANCE_TTL_MINUTES} minute(s) TTL"
            )
            while not self._should_stop:
                await asyncio.sleep(10)  # Check every 10 seconds for more responsive TTL

                minutes_inactive = self.get_minutes_since_last_activity()
                copilot_debug(
                    f"🔍 TTL check for '{self.identifier}': {minutes_inactive:.2f} minutes inactive (limit: {MCP_INSTANCE_TTL_MINUTES})"
                )

                if minutes_inactive >= MCP_INSTANCE_TTL_MINUTES:
                    await self._handle_ttl_expiry(minutes_inactive)
                    break
                elif minutes_inactive >= MCP_INSTANCE_TTL_MINUTES * 0.7:
                    self._log_ttl_warning(minutes_inactive)
        except asyncio.CancelledError:
            copilot_debug(f"🛑 TTL monitor for '{self.identifier}' cancelled")
            raise
        except Exception as e:
            copilot_error(f"❌ Error in TTL monitor for '{self.identifier}': {e}")

    async def _handle_ttl_expiry(self, minutes_inactive: float):
        """Handle TTL expiry by stopping the server."""
        copilot_warning(
            f"⏱️ MCP instance '{self.identifier}' has been inactive for "
            f"{minutes_inactive:.1f} minutes (limit: {MCP_INSTANCE_TTL_MINUTES}). "
            f"Terminating server automatically..."
        )

        self._should_stop = True

        # Stop uvicorn server first if available
        if self.uvicorn_server is not None:
            copilot_debug(f"🛑 Shutting down uvicorn server for '{self.identifier}' on port {self.port}")
            self.uvicorn_server.should_exit = True
            if hasattr(self.uvicorn_server, "force_exit"):
                self.uvicorn_server.force_exit = True

            # Force close the server sockets to free up the port immediately
            if hasattr(self.uvicorn_server, "servers"):
                for server in self.uvicorn_server.servers:
                    server.close()
                    copilot_debug(f"🔒 Closed server socket for port {self.port}")

        # Stop the server task and wait for proper shutdown
        if self.server_task is not None:
            copilot_debug(f"🛑 Stopping MCP server task for '{self.identifier}'")
            self.server_task.cancel()
            try:
                await self.server_task
            except asyncio.CancelledError:
                copilot_debug(f"Server task for '{self.identifier}' cancelled successfully")
                # Don't re-raise here as we're in the TTL monitor - this is intentional cleanup
            finally:
                self.server_task = None
                self.uvicorn_server = None
                copilot_info(f"🔒 MCP server for '{self.identifier}' stopped by TTL monitor")

        # Return the port to the available pool for reuse
        if self.port is not None and self.server_ref:
            await asyncio.sleep(0.1)  # Brief wait for OS to release the port
            self.server_ref.used_ports.discard(self.port)
            self.server_ref.available_ports.add(self.port)
            copilot_debug(f"🔓 Port {self.port} returned to available pool for reuse")

        # Notify main server to remove the instance
        if self.server_ref:
            self.server_ref.remove_instance(self.identifier)

        copilot_info(f"✅ TTL monitor for '{self.identifier}' completed termination process")

    def _log_ttl_warning(self, minutes_inactive: float):
        """Log TTL warning message."""
        copilot_debug(
            f"⚠️ MCP instance '{self.identifier}' has been inactive for {minutes_inactive:.1f} minutes "
            f"(will be terminated in {MCP_INSTANCE_TTL_MINUTES - minutes_inactive:.1f} minutes)"
        )

    async def start_server(self):
        """Start the FastMCP server for this instance."""
        if self.server_task is not None:
            copilot_warning(f"⚠️ MCP server for {self.identifier} is already running on port {self.port}")
            return

        # Assign a free port if not already assigned
        if self.port is None:
            self.port = self._find_free_port()
            copilot_debug(f"� Found free port {self.port} for '{self.identifier}'")

        try:
            copilot_debug(f"�🚀 Starting MCP server for '{self.identifier}' on port {self.port}...")

            # Use the FastMCP app that was already configured with tools in __init__
            # No need to create a new one or register tools again

            # Create uvicorn config and server
            config = uvicorn.Config(
                app=self.mcp.http_app(),
                host="localhost",
                port=self.port,
                log_level="error",  # Use error to minimize logs
            )

            self.uvicorn_server = uvicorn.Server(config)

            # Store creation timestamp
            self.created_at = time.time()

            # Start server in background task
            self.server_task = asyncio.create_task(self.uvicorn_server.serve())

            # Give it a moment to start
            await asyncio.sleep(0.5)

            # Start TTL monitor
            self.ttl_task = asyncio.create_task(self._ttl_monitor())

            # Update initial activity
            self.update_activity()

            copilot_info(
                f"🎯 MCP server for '{self.identifier}' successfully started on port {self.port} "
                f"(TTL: {MCP_INSTANCE_TTL_MINUTES} minutes)"
            )

        except Exception as e:
            copilot_error(f"❌ Failed to start MCP server for {self.identifier}: {e}")
            self.server_task = None
            raise

    async def stop_server(self):
        """Stop the FastMCP server for this instance."""
        self._should_stop = True

        # Stop TTL monitor first
        if self.ttl_task is not None:
            copilot_debug(f"🛑 Stopping TTL monitor for '{self.identifier}'")
            self.ttl_task.cancel()
            try:
                await self.ttl_task
            except asyncio.CancelledError:
                copilot_debug(f"TTL monitor task for '{self.identifier}' cancelled successfully")
                raise  # Re-raise as expected by linter
            finally:
                self.ttl_task = None

        # Stop uvicorn server first if available
        if self.uvicorn_server is not None:
            copilot_debug(f"🛑 Shutting down uvicorn server for '{self.identifier}' on port {self.port}")
            self.uvicorn_server.should_exit = True
            if hasattr(self.uvicorn_server, "force_exit"):
                self.uvicorn_server.force_exit = True

        if self.server_task is not None:
            copilot_debug(f"🛑 Stopping MCP server for '{self.identifier}' on port {self.port}")
            self.server_task.cancel()
            try:
                await self.server_task
            except asyncio.CancelledError:
                copilot_debug(f"Server task for '{self.identifier}' cancelled successfully")
                raise  # Re-raise as expected by linter
            finally:
                self.server_task = None
                self.uvicorn_server = None

                # Return the port to the available pool for reuse
                if self.port is not None and self.server_ref:
                    self.server_ref.used_ports.discard(self.port)
                    self.server_ref.available_ports.add(self.port)
                    copilot_debug(f"🔓 Port {self.port} returned to available pool for reuse")

                copilot_info(f"🔒 MCP server for '{self.identifier}' stopped")

    def get_url(self) -> str:
        """Get the URL for this MCP instance."""
        if self.port is None:
            raise RuntimeError("Server not started")
        return f"http://localhost:{self.port}"


class SimplifiedDynamicMCPServer:
    """A simplified dynamic MCP server using FastAPI."""

    def __init__(self, host: str = "localhost", port: int = 5007):
        self.host = host
        self.port = port
        self.instances: Dict[str, DynamicMCPInstance] = {}
        self.used_ports: set[int] = set()  # Track all ports currently in use
        self.available_ports: set[int] = set()  # Pool of ports that were used but are now available

        copilot_info(f"SimplifiedDynamicMCPServer initialized on {host}:{port}")

    def create_app(self) -> FastAPI:
        """Create the FastAPI application with MCP routing."""
        app = FastAPI(title="Etendo Copilot Dynamic MCP Server")

        # Add CORS middleware
        app.add_middleware(
            CORSMiddleware,
            allow_origins=["*"],
            allow_credentials=True,
            allow_methods=["*"],
            allow_headers=["*"],
        )

        # Add basic endpoints
        self._add_basic_endpoints(app)

        # Add MCP routing
        self._add_mcp_routing(app)

        return app

    def _add_basic_endpoints(self, app: FastAPI):
        """Add basic server endpoints."""

        @app.get("/")
        async def root():
            """Root endpoint with server information."""
            return {
                "message": "Etendo Copilot Dynamic MCP Server",
                "version": "1.0.0",
                "description": "Creates MCP instances on-demand based on identifier",
                "usage": "Access MCP instances at /{identifier}/mcp",
                "active_instances": len(self.instances),
                "ttl_minutes": MCP_INSTANCE_TTL_MINUTES,
                "ttl_description": f"Instances are automatically terminated after {MCP_INSTANCE_TTL_MINUTES} minutes of inactivity",
                "port_management": {
                    "used_ports": len(self.used_ports),
                    "available_for_reuse": len(self.available_ports),
                    "total_ports_tracked": len(self.used_ports) + len(self.available_ports),
                },
            }

        @app.get("/health")
        async def health():
            """Health check endpoint."""
            return {
                "status": "healthy",
                "timestamp": datetime.now().isoformat(),
                "active_instances": len(self.instances),
                "port_stats": {
                    "used_ports": sorted(self.used_ports),
                    "available_ports": sorted(self.available_ports),
                    "total_tracked": len(self.used_ports) + len(self.available_ports),
                },
            }

        @app.get("/instances")
        async def list_instances():
            """List all active MCP instances."""
            instances_info = {}
            for identifier, instance in self.instances.items():
                minutes_since_activity = instance.get_minutes_since_last_activity()
                minutes_remaining = max(0, MCP_INSTANCE_TTL_MINUTES - minutes_since_activity)

                instances_info[identifier] = {
                    "created_at": datetime.fromtimestamp(instance.created_at).isoformat(),
                    "last_activity": datetime.fromtimestamp(instance.last_activity).isoformat(),
                    "seconds_alive": int(time.time() - instance.created_at),
                    "minutes_since_last_activity": round(minutes_since_activity, 1),
                    "minutes_until_ttl": round(minutes_remaining, 1),
                    "ttl_minutes": MCP_INSTANCE_TTL_MINUTES,
                    "port": instance.port,
                    "url": instance.get_url() if instance.port else None,
                    "status": "running" if instance.server_task else "stopped",
                }

            return {"active_instances": len(self.instances), "instances": instances_info}

    def _add_mcp_routing(self, app: FastAPI):
        """Add MCP routing endpoints."""

        @app.api_route("/{identifier}/mcp", methods=["GET", "POST", "OPTIONS"])
        @app.api_route("/{identifier}/mcp/{path:path}", methods=["GET", "POST", "OPTIONS"])
        async def handle_mcp_request(identifier: str, request: Request, path: str = ""):
            """Handle MCP requests and create instances on-demand."""
            return await self._handle_mcp_request(identifier, request, path)

    async def _handle_mcp_request(self, identifier: str, request: Request, path: str = ""):
        """Internal handler for MCP requests."""
        # Validate identifier (alphanumeric + underscores/hyphens)
        if not identifier.replace("-", "").replace("_", "").isalnum():
            raise HTTPException(status_code=400, detail="Invalid identifier format")

        # Extract and validate Etendo token from request
        etendo_token = extract_etendo_token_from_request(request)
        if not etendo_token:
            raise HTTPException(
                status_code=401,
                detail="Authentication required. Please provide a valid Etendo token in headers.",
            )
        etendo_token_wb = normalize_etendo_token(etendo_token)
        ThreadContext.set_data("extra_info", {"auth": {"ETENDO_TOKEN": etendo_token_wb}})

        # Create or get existing MCP instance
        instance = await self._get_or_create_instance(identifier, etendo_token)

        # Update activity every time a request is received
        instance.update_activity()

        # Forward the request to the MCP instance
        return await self._proxy_request(request, instance, path)

    async def _get_or_create_instance(self, identifier: str, etendo_token: str) -> DynamicMCPInstance:
        """Get existing instance or create new one."""
        if identifier not in self.instances:
            copilot_info(f"🆕 Creating new MCP instance for identifier: {identifier}")
            instance = DynamicMCPInstance(identifier=identifier, etendo_token=etendo_token, server_ref=self)
            self.instances[identifier] = instance

            # Start the MCP server for this instance
            await instance.start_server()
            copilot_info(f"✅ MCP instance for '{identifier}' created and started on port {instance.port}")
        else:
            instance = self.instances[identifier]

            # Ensure the server is running
            if instance.server_task is None:
                await instance.start_server()
                copilot_info(f"🔄 Restarted MCP instance for '{identifier}' on port {instance.port}")
            else:
                copilot_debug(f"♻️ Using existing MCP instance for identifier: {identifier}")

        return instance

    async def _proxy_request(self, request: Request, instance: DynamicMCPInstance, path: str = ""):
        """Proxy the request to the MCP instance."""
        # Build the target URL
        target_url = f"{instance.get_url()}"
        # Add /mcp
        target_url += "/mcp/"
        if path:
            target_url += f"{path}"

        copilot_debug(
            f"🔀 Proxying {request.method} /{instance.identifier}/mcp{('/' + path) if path else ''} -> {target_url}"
        )

        # Get request data
        method = request.method
        headers = dict(request.headers)

        # Remove host header to avoid conflicts
        if "host" in headers:
            del headers["host"]

        # Get query parameters
        query_params = dict(request.query_params)

        try:
            # Get request body if present
            body = None
            if method in ["POST", "PUT", "PATCH"]:
                body = await request.body()

            # Make the proxied request
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.request(
                    method=method, url=target_url, headers=headers, params=query_params, content=body
                )
                # Clean headers and check if it's a streaming response
                response_headers = dict(response.headers)
                content_type = response_headers.get("content-type", "")

                if (
                    "text/event-stream" in content_type
                    or response_headers.get("transfer-encoding") == "chunked"
                ):
                    # For streaming responses, remove content-length and use StreamingResponse
                    response_headers.pop("content-length", None)

                    def generate():
                        yield response.content

                    return StreamingResponse(
                        generate(),
                        status_code=response.status_code,
                        headers=response_headers,
                        media_type=content_type,
                    )
                else:
                    # For regular responses, use normal Response
                    return Response(
                        content=response.content,
                        status_code=response.status_code,
                        headers=response_headers,
                    )

        except httpx.ConnectError:
            # If connection fails, try to restart the instance
            logger.warning(f"Failed to connect to MCP instance {instance.identifier}, attempting restart")
            await instance.stop_server()
            await instance.start_server()

            # Try once more
            try:
                async with httpx.AsyncClient(timeout=10.0) as client:
                    response = await client.request(
                        method=method, url=target_url, headers=headers, params=query_params, content=body
                    )
                    # Clean headers and check if it's a streaming response
                    response_headers = dict(response.headers)
                    content_type = response_headers.get("content-type", "")

                    if (
                        "text/event-stream" in content_type
                        or response_headers.get("transfer-encoding") == "chunked"
                    ):
                        # For streaming responses, remove content-length and use StreamingResponse
                        response_headers.pop("content-length", None)

                        def generate():
                            yield response.content

                        return StreamingResponse(
                            generate(),
                            status_code=response.status_code,
                            headers=response_headers,
                            media_type=content_type,
                        )
                    else:
                        # For regular responses, use normal Response
                        return Response(
                            content=response.content,
                            status_code=response.status_code,
                            headers=response_headers,
                        )

            except Exception as e:
                logger.error(f"Failed to proxy request to {instance.identifier} after restart: {e}")
                raise HTTPException(
                    status_code=503, detail=f"MCP instance {instance.identifier} is not responding"
                ) from e

        except Exception as e:
            logger.error(f"Error proxying request to {instance.identifier}: {e}")
            raise HTTPException(
                status_code=502, detail=f"Error communicating with MCP instance {instance.identifier}"
            ) from e

    def remove_instance(self, identifier: str):
        """Remove an instance from the active instances dictionary."""
        if identifier in self.instances:
            copilot_info(f"🗑️ Removing instance '{identifier}' from active instances registry")
            del self.instances[identifier]
        else:
            copilot_warning(f"⚠️ Attempt to remove instance '{identifier}' that doesn't exist in registry")

    async def cleanup(self):
        """Clean up all running MCP instances."""
        if not self.instances:
            copilot_info("🧹 No MCP instances to clean up")
            return

        copilot_info(f"🧹 Cleaning up {len(self.instances)} MCP instances...")
        for identifier, instance in self.instances.items():
            try:
                await instance.stop_server()
                copilot_debug(f"✅ Cleaned up MCP instance: {identifier}")
            except Exception as e:
                copilot_warning(f"⚠️ Error cleaning up instance {identifier}: {e}")

        self.instances.clear()
        # Clear port tracking
        self.used_ports.clear()
        self.available_ports.clear()
        copilot_info("🧼 All MCP instances and ports cleaned up successfully")

    async def start_async(self, host: str = None, port: int = None):
        """Start the simplified dynamic MCP server asynchronously."""
        if host is None:
            host = self.host
        if port is None:
            port = self.port

        app = self.create_app()

        copilot_info(f"🚀 Starting Simplified Dynamic MCP Server on {host}:{port}")
        copilot_info(f"📡 Ready to create MCP instances on-demand at /{'{identifier}'}/mcp")

        config = uvicorn.Config(app=app, host=host, port=port, log_level="info")

        server = uvicorn.Server(config)
        await server.serve()

    def start(self, host: str = None, port: int = None):
        """Start the simplified dynamic MCP server (sync version)."""
        asyncio.run(self.start_async(host, port))


# Global server instance
_global_server: Optional[SimplifiedDynamicMCPServer] = None


def get_simplified_dynamic_mcp_server() -> SimplifiedDynamicMCPServer:
    """Get or create the global simplified dynamic MCP server instance."""
    global _global_server
    if _global_server is None:
        _global_server = SimplifiedDynamicMCPServer()
    return _global_server
