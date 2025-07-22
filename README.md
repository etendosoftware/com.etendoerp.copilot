# Etendo Copilot

Etendo Copilot is an AI-powered assistant that helps users answer questions and complete assignments through different tools and agents. The system supports multiple agent types including `Langchain Agent` and `OpenAI Assistant Agent`, and includes an MCP (Model Context Protocol) server for enhanced AI model interactions.

# How to use Copilot as user
* Make sure [docker](https://docs.docker.com/get-docker/) is installed
* Get the `etendo/etendo_copilot_slim` image from [dockerhub](https://hub.docker.com/repository/docker/etendo/etendo_copilot_slim/tags): `docker pull etendo/etendo_copilot_slim:develop`
* Once image is downloaded:
    * Set your local configuration copying `.env.sample` into `.env` and set the right values
    * Run a container as: ` docker run -it --env-file .env -p <host_machine_port>:<inside_docker_port> -v $(pwd)/:/app/ etendo/etendo_copilot_slim:develop`
    * Make a request sample: `curl -i -X POST -H "Content-Type: application/json" -d '{"question": "What is etendo?"}' http://localhost:<host_machine_port>/question`

## Using Docker Compose
For easier deployment, you can use the provided Docker Compose configuration:

1. Set your local configuration copying `.env.sample` into `.env` and set the right values
2. Run using docker-compose: `docker-compose -f compose/com.etendoerp.copilot.yml up`

# Deploy docker image
This is done automatically from CI for develop and experimental branches.

```
docker build -t etendo/etendo_copilot_slim .
docker push etendo/etendo_copilot_slim
```

# Environment Configuration

Key environment variables to configure in your `.env` file:

* `COPILOT_PORT`: Main FastAPI server port (default: 5000)
* `COPILOT_PORT_MCP`: MCP server port (default: 5007)
* `COPILOT_PORT_DEBUG`: Debug port for development
* `AGENT_TYPE`: Agent type (`langchain` or `openai-assistant`)
* `OPENAI_API_KEY`: Your OpenAI API key
* `OPENAI_MODEL`: OpenAI model to use (default: gpt-4-1106-preview)
* `GOOGLE_API_KEY`: Google API key for Google AI models
* `BASTIAN_URL`: URL for Bastian service integration

# Backend Development

## Virtual environment

As tool for managing multiple Python versions you can use [pyenv](https://github.com/pyenv/pyenv). The project now uses [uv](https://docs.astral.sh/uv/) for package management, which is faster and more reliable than Poetry.

* Install `pyenv`: https://github.com/pyenv/pyenv#installation
* Install `uv`: `curl -LsSf https://astral.sh/uv/install.sh | sh`
* Create `etendo-copilot-core` environment using uv:

```bash
pyenv install 3.12
pyenv local 3.12
uv venv
source .venv/bin/activate  # On macOS/Linux
# or .venv\Scripts\activate  # On Windows
uv pip install -r requirements.txt
```

Alternative approach using pyproject.toml:
```bash
uv sync
```

Alternative, you can use Docker.

### Torch and MacOS issue
If you are getting this issue from `uv sync` or `uv pip install`: `Unable to find installation candidates for torch`.

Workaround:
```bash
source .venv/bin/activate
uv pip install torch
```

Verify installation:
```bash
python -c "import torch; print(torch.__version__)"
```

### Add new dependencies
* For prod dependency: Add to `pyproject.toml` dependencies section or run: `uv add <dep_name>`
* For dev dependency: Add to `pyproject.toml` dev dependencies or run: `uv add <dep_name> --dev`

#### Exporting dependencies
* To export dependencies to `requirements.txt` run: `uv pip freeze > requirements.txt` or `uv export --format requirements-txt > requirements.txt`

## How to run copilot
* Locally outside docker:
	- Copy `.env.sample` into `.env` and set the right values
	- `python run.py` (make sure your virtual environment is activated)

* Using docker, make sure `.env` is created and all the variables are set, only then run `docker run --env-file .env -p 5000:5000 etendo/etendo_copilot_slim`. You can set the port that you want, just be sure to set the same port in the image from `.env` if not, the api will never be reached.

* Using docker-compose: `docker-compose -f compose/com.etendoerp.copilot.yml up`

* The `AGENT_TYPE` environment variable should be used to set the agent type. There are two available agent: `langchain` and `openai-assistant`. By default copilot will be executed for `langchain`.

* Mount code as volume: `docker run --env-file .env -p 5000:5000 -v $(pwd)/copilot:/app/copilot etendo/etendo_copilot_slim`.

## How to run unit test
`python -m pytest tests` (make sure your virtual environment is activated)

## OpenAPI URL
You can get the open api (swagger) documentation from `http://localhost:<port>/docs` or `http://localhost:<port>/redoc`

## MCP Server (Model Context Protocol)

The MCP module for Etendo Copilot provides a comprehensive Model Context Protocol server implementation using FastMCP with modern HTTP streaming transport, including support for both static and dynamic MCP instances.

### Overview

This module implements a **Dynamic MCP Server** with on-demand creation of isolated MCP instances based on URL identifiers.

### Key Features

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

### Quick Start

The dynamic MCP server creates isolated instances based on URL identifiers:

```bash
# Creates/connects to 'company1' instance
curl http://localhost:5007/company1/mcp

# Creates/connects to 'project-alpha' instance
curl http://localhost:5007/project-alpha/mcp

# Creates/connects to 'tenant-123' instance
curl http://localhost:5007/tenant-123/mcp
```

### MCP Configuration

#### Environment Variables

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `COPILOT_PORT_MCP` | MCP server port | `5007` |
| `COPILOT_PORT_DEBUG` | Enable debug mode if defined | `false` |

#### Configuration Example

```bash
# Custom port configuration
export COPILOT_PORT_MCP=8080

# Enable debug mode
export COPILOT_PORT_DEBUG=5100
```

### Architecture

#### Dynamic MCP System Architecture

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

#### URL Pattern Examples

- `http://localhost:5007/company1/mcp` → Creates instance for "company1"
- `http://localhost:5007/project-alpha/mcp` → Creates instance for "project-alpha"
- `http://localhost:5007/tenant-123/mcp` → Creates instance for "tenant-123"

### Session Flow

#### 1. Client Connection
The client connects to the MCP server without specifying an initial agent.

#### 2. Session Initialization
The client should call `init_session` with the desired `agent_id`:

```json
{
  "tool": "init_session",
  "arguments": {
    "agent_id": "my-custom-agent"
  }
}
```

#### 3. Tool Usage
After session initialization, all tools will use the specified agent context.

### Available Tools

#### Built-in Tools

All MCP instances include these tools:

- **`ping`**: Health check tool
- **`init_session`**: Initialize session with agent ID
- **`agent_greeting`**: Get personalized greeting for current agent
- **`hello_world`**: Custom greeting with instance information
- **`server_info`**: Get basic MCP server information
- **`get_agent_info`**: Get information about current session agent

#### Tool Examples

**ping**
```bash
curl -X POST http://localhost:5007/tools/ping
# Response: "pong"
```

**server_info**
```bash
curl -X POST http://localhost:5007/tools/server_info
# Response: {"name": "etendo-copilot-mcp", "version": "0.1.0", ...}
```

**init_session**
```bash
curl -X POST http://localhost:5007/tools/init_session \
  -H "Content-Type: application/json" \
  -d '{"agent_id": "sales-assistant"}'
# Response: "Sesión inicializada para el agente: sales-assistant"
```

**agent_greeting**
```bash
curl -X POST http://localhost:5007/tools/agent_greeting
# Response: "¡El agente sales-assistant te envía saludos!"
```

#### Dynamic Instance hello_world Tool

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

### Monitoring and Health Checks

#### Health Check Endpoint
```bash
GET http://localhost:5007/health
```

#### Instance Listing
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

### Usage Examples

#### Complete Client Flow

```python
import httpx

# 1. Connect to MCP server
async with httpx.AsyncClient() as client:
    # 2. Initialize session with agent_id
    init_response = await client.post(
        "http://localhost:5007/tools/init_session",
        json={"agent_id": "my-custom-agent"}
    )
    print(init_response.json())

    # 3. Use tools with session agent
    greeting_response = await client.post(
        "http://localhost:5007/tools/agent_greeting",
        json={}
    )
    print(greeting_response.json())

    # 4. Check current agent
    info_response = await client.post(
        "http://localhost:5007/tools/get_agent_info",
        json={}
    )
    print(info_response.json())
```

#### Manual Server Start

```python
from copilot.core.mcp.simplified_dynamic_utils import start_simplified_dynamic_mcp_with_cleanup

# Start with environment configuration
success = start_simplified_dynamic_mcp_with_cleanup()

if success:
    print("MCP server started successfully")
```

### Integration with MCP Clients

#### Claude Desktop

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

#### Custom Client

```python
import httpx

# Connect to MCP server
async with httpx.AsyncClient() as client:
    # 1. Initialize session
    await client.post(
        "http://localhost:5007/tools/init_session",
        json={"agent_id": "my-assistant"}
    )

    # 2. Use tools
    response = await client.post(
        "http://localhost:5007/tools/agent_greeting",
        json={}
    )
    print(response.json())
```

### Development

#### Adding Custom Tools

To add tools to dynamic instances, modify the `_setup_tools()` method:

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

#### Basic Tools
```python
# In tools/basic_tools.py
def register_basic_tools(app):
    @app.tool()
    def my_basic_tool(parameter: str) -> str:
        """Description of my basic tool."""
        return f"Result: {parameter}"
```

#### Session Tools
```python
# In tools/session_tools.py
def register_session_tools(app):
    @app.tool()
    def my_session_tool(parameter: str) -> str:
        """Description of my session tool."""
        # Access current session agent_id
        agent_id = current_agent_id.get()
        return f"Result from {agent_id}: {parameter}"
```

### Cleanup and Lifecycle

#### Automatic Cleanup
The system automatically handles cleanup on application shutdown:

- Graceful shutdown of all dynamic instances
- Port release and resource cleanup
- Signal handlers for SIGTERM/SIGINT

#### Manual Cleanup
```python
from copilot.core.mcp import get_simplified_dynamic_mcp_manager

manager = get_simplified_dynamic_mcp_manager()
manager.stop()
```

### Troubleshooting

#### Common Issues

1. **Port conflicts**: The system automatically finds free ports starting from 8000
2. **Instance not starting**: Check logs for specific error messages
3. **Connection refused**: Ensure the main server is running on port 5007

#### Port in Use
```bash
# Check occupied port
lsof -i :5007

# Change port
export COPILOT_PORT_MCP=5008
```

#### Check Status
```bash
# Check connectivity
curl http://localhost:5007/health

# View logs
tail -f logs/copilot.log | grep MCP
```

### Module Structure

```
copilot/core/mcp/
├── __init__.py                    # Module exports
├── README.md                      # MCP documentation
├── simplified_dynamic_server.py   # Dynamic MCP server implementation
├── simplified_dynamic_manager.py  # Dynamic MCP lifecycle manager
├── simplified_dynamic_utils.py    # Dynamic MCP utilities and cleanup
├── tools/                         # MCP tools directory
│   ├── __init__.py               # Tool exports
│   ├── base.py                   # Base classes for tools
│   ├── session_tools.py          # Session tools
│   └── basic_tools.py            # Basic tools
└── resources/                     # MCP resources directory
    └── base.py                   # Base classes for resources
```

### References

- [FastMCP Documentation](https://github.com/jlowin/fastmcp)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Claude Desktop MCP Configuration](https://docs.anthropic.com/claude/docs/mcp)

## Pre-commit
* Install pre-commit from [HERE](https://pre-commit.com/#install)
* Setup pre-commit `pre-commit install & pre-commit autoupdate`
* If you want to run for all the files: `pre-commit run --all-files`

# Third Party Tools Implementation

Any developer can define his own tools and attach them into copilot agent. So as to do this the third party tools **MUST** be added into the `tools` package.

## Baby steps to define a new tool from copilot source code

1- Create a new python module inside `tools` package: `hello_world.py`

2- Extend the ToolWrapper class from copilot.core.tool_wrapper and set your own tool implementation. Boilerplate sample:

```py
from copilot.core.tool_wrapper import ToolWrapper

class MyTool(ToolWrapper):
    name = 'my_tool_name'
    description = 'My tool description'

    def __call__(self, *args, **kwargs):
        # Implement your tool's logic HERE
```

3- Enable the new tool from `tools_config.json` under `third_party_tools`:
```
{
    "native_tools": {
        ...
    },
    "third_party_tools": {
        "MyTool": true
    }
}
```

4- Restart the copilot container loading the project root folder through a volume: `docker run --env-file .env -p 5000:5000 -v $(pwd):/app etendo/etendo_copilot_slim`


## Baby steps to define a new tool just using copilot image

1- Create a `tools` directory and inside it create a `__init__.py` file.

2- Create a new python module inside `tools` package: `hello_world.py`

3- Extend the ToolWrapper class from copilot.core.tool_wrapper and set your own tool implementation. Boilerplate sample:

```py
from copilot.core.tool_wrapper import ToolWrapper

class MyTool(ToolWrapper):
    name = 'my_tool_name'
    description = 'My tool description'

    def __call__(self, *args, **kwargs):
        # Implement your tool's logic HERE
```

4- Expose the new tool class name from `__init__.py`

```py
from .hello_world import MyTool
```

5- Enable the new tool from `tools_config.json` under `third_party_tools`:
```
{
    "native_tools": {
        ...
    },
    "third_party_tools": {
        "MyTool": true
    }
}
```

6- Restart the copilot container loading the project root folder through a volume: `docker run --env-file .env -p 5000:5000 -v $(pwd)/tools:/app/tools -v $(pwd)/tools_config.json:/app/tools_config.json etendo/etendo_copilot_slim`

## Third Party Tools dependencies
Formats:
* `pandas`                => Installing latest version
* `pandas==1.3.3`         => Installing a specific version
* `pandas>=1.0.3`         => Greater than or equal to a certain version
* `pandas<=1.2.4`         => Less than or equal to a certain version
* `pandas>1.0.0`          => Greater than a certain version
* `pandas<2.0.0`          => Less than a certain version
* `pandas>=1.0.0,<=2.0.0` => Using version ranges
* `pandas~=1.0.0`         => Tilde operator (~) for installing compatible versions
* `pandas^1.0.0`          => Caret operator (^) for installing compatible versions

# Resources
* [Langchain Agents](https://python.langchain.com/docs/modules/agents/)
* [Assistants API](https://platform.openai.com/docs/assistants/overview)
* [How Assistants work](https://platform.openai.com/docs/assistants/how-it-works)
