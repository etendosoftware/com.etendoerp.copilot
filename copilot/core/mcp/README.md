# MCP Module - Etendo Copilot

El módulo MCP (Model Context Protocol) de Etendo Copilot proporciona un servidor compatible con el protocolo MCP usando FastMCP con transporte HTTP streaming moderno.

## Características

- ✅ Servidor MCP con FastMCP 2.1.2
- ✅ Transporte HTTP streaming (moderno, reemplaza SSE)
- ✅ Siempre habilitado (no requiere configuración adicional)
- ✅ Configuración personalizable por variables de entorno
- ✅ **Identificación de agente por sesión** (enviado por el cliente)
- ✅ Herramientas integradas (ping, init_session, agent_greeting)

## Configuración

### Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `COPILOT_PORT_MCP` | Puerto del servidor MCP | `5007` |
| `COPILOT_PORT_DEBUG` | Habilita modo debug si está definido | `false` |

### Ejemplo de Configuración

```bash
# Configurar puerto personalizado
export COPILOT_PORT_MCP=8080

# Habilitar modo debug
export COPILOT_PORT_DEBUG=5100
```

## Flujo de Sesión

### 1. Conexión del Cliente
El cliente se conecta al servidor MCP sin especificar un agente inicial.

### 2. Inicialización de Sesión
El cliente debe llamar a `init_session` con el `agent_id` deseado:

```json
{
  "tool": "init_session",
  "arguments": {
    "agent_id": "mi-agente-personalizado"
  }
}
```

### 3. Uso de Herramientas
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
