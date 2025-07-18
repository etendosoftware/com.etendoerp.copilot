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
├── server.py                      # Static MCP server implementation
├── manager.py                     # Static MCP lifecycle manager
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
Una vez inicializada la sesión, todas las herramientas usarán el `agent_id` de la sesión actual.

## Herramientas Disponibles

### Herramientas Básicas

#### 1. ping
Herramienta básica para verificar conectividad MCP.

**Respuesta:** `"pong"`

#### 2. server_info
Obtiene información básica del servidor MCP.

**Respuesta:**
```json
{
  "name": "etendo-copilot-mcp",
  "version": "0.1.0",
  "description": "Etendo Copilot MCP Server with HTTP streaming",
  "transport": "http-streaming",
  "status": "running"
}
```

### Herramientas de Sesión

#### 3. init_session
Inicializa una nueva sesión con un `agent_id` específico.

**Parámetros:**
- `agent_id` (string): Identificador del agente para esta sesión

**Respuesta:** `"Sesión inicializada para el agente: {agent_id}"`

**Ejemplo:**
```json
{
  "tool": "init_session",
  "arguments": {
    "agent_id": "asistente-ventas"
  }
}
```

#### 4. agent_greeting
Herramienta que devuelve un saludo personalizado del agente de la sesión actual.

**Respuesta:** `"¡El agente {agent_id} te envía saludos!"`

**Nota:** Requiere que la sesión haya sido inicializada con `init_session` primero.

#### 5. get_agent_info
Obtiene información sobre el agente de la sesión actual.

**Respuesta:** `"Agente actual: {agent_id}"`

## Uso

### Flujo Completo de Cliente

```python
import httpx

# 1. Conectar al servidor MCP
async with httpx.AsyncClient() as client:
    # 2. Inicializar sesión con agent_id
    init_response = await client.post(
        "http://localhost:5007/tools/init_session",
        json={"agent_id": "mi-agente-personalizado"}
    )
    print(init_response.json())  # "Sesión inicializada para el agente: mi-agente-personalizado"

    # 3. Usar herramientas con el agente de la sesión
    greeting_response = await client.post(
        "http://localhost:5007/tools/agent_greeting",
        json={}
    )
    print(greeting_response.json())  # "¡El agente mi-agente-personalizado te envía saludos!"

    # 4. Verificar agente actual
    info_response = await client.post(
        "http://localhost:5007/tools/get_agent_info",
        json={}
    )
    print(info_response.json())  # "Agente actual: mi-agente-personalizado"
```

### Inicio Automático

El servidor MCP se inicia automáticamente al ejecutar `run.py`:

```python
# En run.py se llama automáticamente
start_mcp_with_cleanup()
```

### Inicio Manual

```python
from copilot.core.mcp.manager import start_mcp_server_from_env

# Iniciar con configuración de entorno
success = start_mcp_server_from_env()

if success:
    print("Servidor MCP iniciado correctamente")
```

### Configuración Personalizada

```python
from copilot.core.mcp.server import MCPServerConfig, MCPServer

# Crear configuración personalizada
config = MCPServerConfig(
    host="localhost",
    port=8080,
    debug=True
)

# Crear e iniciar servidor
server = MCPServer(config)
await server.start_async()
```

## Arquitectura

### Componentes Principales

```
copilot/core/mcp/
├── __init__.py          # Exports del módulo
├── server.py            # Servidor MCP principal
├── manager.py           # Gestión de ciclo de vida
├── utils.py             # Utilidades y cleanup
├── tools/              # Herramientas MCP
│   ├── __init__.py     # Exports de herramientas
│   ├── base.py         # Clases base para herramientas
│   ├── session_tools.py # Herramientas de sesión
│   └── basic_tools.py  # Herramientas básicas
└── resources/          # Recursos MCP
    └── base.py         # Clases base para recursos
```

### Flujo de Inicio

1. **Configuración**: Lee variables de entorno (puerto, debug)
2. **Servidor**: Crea instancia MCPServer con FastMCP
3. **Herramientas**: Registra herramientas usando decoradores `@app.tool()`
4. **Sesiones**: Maneja `agent_id` por sesión usando ContextVar
5. **Transporte**: Inicia HTTP streaming en puerto configurado
6. **Threading**: Ejecuta en hilo daemon paralelo al servidor principal

### Transporte HTTP Streaming

- **Protocolo**: HTTP streaming nativo (moderno)
- **Ventajas**: Bidireccional, eficiente, estándar
- **Compatibilidad**: Reemplaza SSE y WebSockets
- **Implementación**: `FastMCP.run()` y `FastMCP.run_async()`

## Desarrollo

### Agregar Nuevas Herramientas

#### Herramientas Básicas
```python
# En tools/basic_tools.py
def register_basic_tools(app):
    @app.tool()
    def mi_herramienta_basica(parametro: str) -> str:
        \"\"\"Descripción de mi herramienta básica.\"\"\"
        return f"Resultado: {parametro}"
```

#### Herramientas de Sesión
```python
# En tools/session_tools.py
def register_session_tools(app):
    @app.tool()
    def mi_herramienta_sesion(parametro: str) -> str:
        \"\"\"Descripción de mi herramienta de sesión.\"\"\"
        # Acceder al agent_id de la sesión actual
        agent_id = current_agent_id.get()
        return f"Resultado de {agent_id}: {parametro}"
```

### Agregar Recursos

```python
# En server.py, dentro de _setup_server()
@self.app.resource()
def mi_recurso() -> dict:
    \"\"\"Descripción de mi recurso.\"\"\"
    return {"tipo": "recurso", "datos": "contenido"}
```

## Troubleshooting

### Puerto en Uso
```bash
# Verificar puerto ocupado
lsof -i :5007

# Cambiar puerto
export COPILOT_PORT_MCP=5008
```

### Verificar Estado
```bash
# Verificar conectividad
curl http://localhost:5007/health

# Ver logs
tail -f logs/copilot.log | grep MCP
```

### Testing
```python
# Probar inicialización de sesión
from copilot.core.mcp.server import MCPServer, current_agent_id

server = MCPServer()

# Simular inicialización de sesión
current_agent_id.set("agente-test")
print(current_agent_id.get())  # "agente-test"
```

## Integración con Cliente MCP

### Claude Desktop

Agregar en `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "etendo-copilot": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "http://localhost:5007/mcp"
      ]
    }
  }
}
```

### Cliente Personalizado

```python
import httpx

# Conectar al servidor MCP
async with httpx.AsyncClient() as client:
    # 1. Inicializar sesión
    await client.post(
        "http://localhost:5007/tools/init_session",
        json={"agent_id": "mi-asistente"}
    )

    # 2. Usar herramientas
    response = await client.post(
        "http://localhost:5007/tools/agent_greeting",
        json={}
    )
    print(response.json())  # "¡El agente mi-asistente te envía saludos!"
```

## Logs y Monitoring

El servidor MCP registra eventos importantes:

```
[INFO] Starting MCP server with HTTP streaming on localhost:5007
[INFO] Setting up MCP server: etendo-copilot-mcp for agent: mi-agente
[INFO] MCP server initialized with minimal configuration
[INFO] MCP server thread started
```

## Notas de Implementación

- **Threading**: Ejecuta en hilo daemon para no bloquear FastAPI
- **Cleanup**: Registra handlers para limpieza automática al cerrar
- **Error Handling**: Manejo robusto de errores de configuración y red
- **Escalabilidad**: Diseñado para agregar herramientas y recursos fácilmente
