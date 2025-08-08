# MCP (Model Context Protocol) Module - Etendo Copilot

The MCP module for Etendo Copilot provides a comprehensive Model Context Protocol server implementation using FastMCP with modern HTTP streaming transport, including support for both static and dynamic MCP instances.

## 🎯 Overview

This module implements two MCP server modes:

1. **Static MCP Server**: Traditional single-instance MCP server
2. **Dynamic MCP Server**: On-demand creation of isolated MCP instances based on URL identifiers

## ✨ Key Features

- ✅ **FastMCP 2.1.2+** compatible server implementation
- ✅ **Modern HTTP streaming** transport (replaces SSE)
- ✅ **Always enabled** (no additional configuration required)
- ✅ **Dynamic instance creation** on-demand
- ✅ **Multi-tenant support** with isolated MCP instances
- ✅ **Automatic port allocation** for dynamic instances
- ✅ **Transparent proxy routing** for request forwarding
- ✅ **Custom hello_world tool** with instance-specific information
- ✅ **Lifecycle management** with automatic cleanup
- ✅ **Monitoring APIs** for health checks and instance listing
- ✅ **Session-based agent identification** (sent by client)

## 🚀 Quick Start

### Static MCP Server

The static MCP server runs on a single port and handles all requests:

```bash
# Default port: 5007
curl http://localhost:5007/mcp
```

### Dynamic MCP Server

The dynamic MCP server creates isolated instances based on URL identifiers:

```bash
# Creates/connects to 'company1' instance
curl http://localhost:5007/company1/mcp

# Creates/connects to 'project-alpha' instance
curl http://localhost:5007/project-alpha/mcp

# Creates/connects to 'tenant-123' instance
curl http://localhost:5007/tenant-123/mcp
```

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `COPILOT_PORT_MCP` | MCP server port | `5007` |
| `COPILOT_PORT_DEBUG` | Enable debug mode if defined | `false` |

### Configuration Example

```bash
# Custom port configuration
export COPILOT_PORT_MCP=8080

# Enable debug mode
export COPILOT_PORT_DEBUG=5100
```

## 🏗️ Architecture

### Dynamic MCP System Architecture

```
MCP Client
    ↓
http://localhost:5007/IDENTIFIER/mcp
    ↓
Main FastAPI Server (port 5007)
    ↓ (proxy routing)
Dynamic MCP Instance "IDENTIFIER" (dynamic port, e.g., 8001)
    ↓
Isolated tools and resources
```

### URL Pattern Examples

- `http://localhost:5007/company1/mcp` → Creates instance for "company1"
- `http://localhost:5007/project-alpha/mcp` → Creates instance for "project-alpha"
- `http://localhost:5007/tenant-123/mcp` → Creates instance for "tenant-123"

## 📋 Session Flow

### 1. Client Connection
The client connects to the MCP server without specifying an initial agent.

### 2. Session Initialization
The client should call `init_session` with the desired `agent_id`:

```json
{
  "tool": "init_session",
  "arguments": {
    "agent_id": "my-custom-agent"
  }
}
```

### 3. Tool Usage
After session initialization, all tools will use the specified agent context.

## 🛠️ Available Tools

### Built-in Tools

All MCP instances (static and dynamic) include these tools:

- **`ping`**: Health check tool
- **`init_session`**: Initialize session with agent ID
- **`agent_greeting`**: Get personalized greeting for current agent
- **`hello_world`**: Custom greeting with instance information (dynamic only)

### Dynamic Instance hello_world Tool

Each dynamic MCP instance includes a personalized `hello_world` tool:

```json
{
  "tool": "hello_world",
  "arguments": {}
}
```

**Example Response:**
```
"Hello! You are connected to company1 MCP! Created 45 seconds ago"
```

## 📁 Module Structure

```
copilot/core/mcp/
├── __init__.py                    # Module exports
├── README.md                      # This documentation
├── simplified_dynamic_server.py   # Dynamic MCP server implementation
├── simplified_dynamic_manager.py  # Dynamic MCP lifecycle manager
├── simplified_dynamic_utils.py    # Dynamic MCP utilities and cleanup
├── tools/                         # MCP tools directory
├── resources/                     # MCP resources directory
└── utils.py                       # Utility functions
```

## 🚀 Usage Examples

### Starting the Server

The MCP server starts automatically when running the main application:

```python
# In run.py - automatically called
start_simplified_dynamic_mcp_with_cleanup()
```

### Monitoring Dynamic Instances

```bash
# Check server status
curl http://localhost:5007/health

# List active instances
curl http://localhost:5007/instances
```

### Connecting with Claude Desktop

Add to your Claude Desktop configuration:

```json
{
  "mcpServers": {
    "etendo-copilot": {
      "command": "curl",
      "args": ["-X", "GET", "http://localhost:5007/my-company/mcp"]
    }
  }
}
```

## 🔍 Monitoring and Health Checks

### Health Check Endpoint
```bash
GET http://localhost:5007/health
```

### Instance Listing
```bash
GET http://localhost:5007/instances
```

**Response example:**
```json
{
  "active_instances": 3,
  "instances": {
    "company1": {
      "identifier": "company1",
      "created_at": "2025-01-18T10:30:00Z",
      "port": 8001,
      "url": "http://localhost:8001",
      "status": "running"
    },
    "project-alpha": {
      "identifier": "project-alpha",
      "created_at": "2025-01-18T11:15:00Z",
      "port": 8002,
      "url": "http://localhost:8002",
      "status": "running"
    }
  }
}
```

## 🧹 Cleanup and Lifecycle

### Automatic Cleanup
The system automatically handles cleanup on application shutdown:

- Graceful shutdown of all dynamic instances
- Port release and resource cleanup
- Signal handlers for SIGTERM/SIGINT

### Manual Cleanup
```python
from copilot.core.mcp import get_simplified_dynamic_mcp_manager

manager = get_simplified_dynamic_mcp_manager()
manager.stop()
```

## 🐛 Troubleshooting

### Common Issues

1. **Port conflicts**: The system automatically finds free ports starting from 8000
2. **Instance not starting**: Check logs for specific error messages
3. **Connection refused**: Ensure the main server is running on port 5007

### Debugging

Enable debug mode:
```bash
export COPILOT_PORT_DEBUG=5100
```

Check server logs for detailed information about instance creation and request routing.

## 🔧 Development

### Adding Custom Tools

To add tools to dynamic instances, modify the `_setup_tools()` method in `DynamicMCPInstance`:

```python
def _setup_tools(self):
    """Configure tools for this MCP instance."""
    register_basic_tools(self.mcp)
    register_session_tools(self.mcp)

    # Add your custom tools here
    @self.mcp.tool
    def my_custom_tool(param: str) -> str:
        return f"Custom response for {self.identifier}: {param}"
```

### Testing

```python
# Test dynamic instance creation
from copilot.core.mcp.simplified_dynamic_server import DynamicMCPInstance

instance = DynamicMCPInstance("test")
await instance.start_server()
print(f"Instance running on port: {instance.port}")
await instance.stop_server()
```

## 📚 References

- [FastMCP Documentation](https://github.com/jlowin/fastmcp)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Claude Desktop MCP Configuration](https://docs.anthropic.com/claude/docs/mcp)

---

**Version**: 0.1.0
**Last Updated**: January 18, 2025
