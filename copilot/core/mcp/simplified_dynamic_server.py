"""
Simplified Dynamic MCP Server implementation for Etendo Copilot.

This                     copilot_debug(f"🚀 Starting MCP server for '{self.identifier}' on port {self.port}...")        copilot_debug(f"🔍 Found free port {self.port} for '{self.identifier}'") #            copilot_debug(f"🚀 Starting MCP server for '{self.identifier}' on port {self.port}...")Assign a free port if not already assigned
        if self.port is None:
            self.port = self._find_free_port()
            copilot_debug(f"🔍 Found free port {self.port} for '{self.identifier}'")ule provides a working dynamic MCP server that creates instances on-demand
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
from copilot.core.mcp.tools import register_basic_tools, register_session_tools
from copilot.core.utils import (
    copilot_debug,
    copilot_error,
    copilot_info,
    copilot_warning,
)
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from fastmcp import FastMCP

logger = logging.getLogger(__name__)


class DynamicMCPInstance:
    """A single MCP instance that can be created dynamically."""

    def __init__(self, identifier: str):
        self.identifier = identifier
        self.created_at = time.time()
        self.mcp = FastMCP("Etendo Copilot Dynamic MCP")
        self.port: Optional[int] = None
        self.server_task: Optional[asyncio.Task] = None

        # Configure and register tools
        self._setup_tools()

        copilot_info(f"🔧 Setting up DynamicMCPInstance for identifier: {identifier}")

    def _setup_tools(self):
        """Configure tools for this MCP instance."""
        # Register basic tools
        register_basic_tools(self.mcp)
        register_session_tools(self.mcp)

        # Add custom hello_world tool with instance info
        @self.mcp.tool
        def hello_world() -> str:
            """Say hello with instance information."""
            seconds_alive = int(time.time() - self.created_at)
            return f"Hello! You are connected to {self.identifier} MCP! Created {seconds_alive} seconds ago."

        copilot_info(f"✅ Tools configured for MCP instance '{self.identifier}'")

    def _find_free_port(self, start_port: int = 8000) -> int:
        """Find a free port starting from start_port."""
        for port in range(start_port, start_port + 1000):
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                try:
                    s.bind(("localhost", port))
                    return port
                except OSError:
                    continue
        raise RuntimeError("No free ports available")

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

            server = uvicorn.Server(config)

            # Store creation timestamp
            self.created_at = time.time()

            # Start server in background task
            self.server_task = asyncio.create_task(server.serve())

            # Give it a moment to start
            await asyncio.sleep(0.5)

            copilot_info(f"🎯 MCP server for '{self.identifier}' successfully started on port {self.port}")

        except Exception as e:
            copilot_error(f"❌ Failed to start MCP server for {self.identifier}: {e}")
            self.server_task = None
            raise

    async def stop_server(self):
        """Stop the FastMCP server for this instance."""
        if self.server_task is not None:
            copilot_debug(f"🛑 Stopping MCP server for '{self.identifier}' on port {self.port}")
            self.server_task.cancel()
            try:
                await self.server_task
            except asyncio.CancelledError:
                copilot_debug(f"Server task for '{self.identifier}' cancelled successfully")
                # Re-raise the CancelledError as expected
                raise
            finally:
                self.server_task = None
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
            }

        @app.get("/health")
        async def health():
            """Health check endpoint."""
            return {
                "status": "healthy",
                "timestamp": datetime.now().isoformat(),
                "active_instances": len(self.instances),
            }

        @app.get("/instances")
        async def list_instances():
            """List all active MCP instances."""
            instances_info = {}
            for identifier, instance in self.instances.items():
                instances_info[identifier] = {
                    "created_at": instance.created_at.isoformat(),
                    "seconds_alive": int((datetime.now() - instance.created_at).total_seconds()),
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

        # Create or get existing MCP instance
        instance = await self._get_or_create_instance(identifier)

        # Forward the request to the MCP instance
        return await self._proxy_request(request, instance, path)

    async def _get_or_create_instance(self, identifier: str) -> DynamicMCPInstance:
        """Get existing instance or create new one."""
        if identifier not in self.instances:
            copilot_info(f"🆕 Creating new MCP instance for identifier: {identifier}")
            instance = DynamicMCPInstance(identifier)
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
        target_url += "/mcp"
        if path:
            target_url += f"/{path}"

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

                # Return the response
                return Response(
                    content=response.content, status_code=response.status_code, headers=dict(response.headers)
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

                    return Response(
                        content=response.content,
                        status_code=response.status_code,
                        headers=dict(response.headers),
                    )
            except Exception as e:
                logger.error(f"Failed to proxy request to {instance.identifier} after restart: {e}")
                raise HTTPException(
                    status_code=503, detail=f"MCP instance {instance.identifier} is not responding"
                )

        except Exception as e:
            logger.error(f"Error proxying request to {instance.identifier}: {e}")
            raise HTTPException(
                status_code=502, detail=f"Error communicating with MCP instance {instance.identifier}"
            )

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
        copilot_info("🧼 All MCP instances cleaned up successfully")

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
