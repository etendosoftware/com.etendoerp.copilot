# Dynamic MCP Server - Etendo Copilot

Este módulo implementa un servidor MCP dinámico que crea instancias de servidores MCP bajo demanda basándose en identificadores de URL.

## ✨ Características

- ✅ **Creación dinámica**: Los servidores MCP se crean solo cuando alguien se conecta
- ✅ **Múltiples instancias**: Cada identificador tiene su propio servidor MCP aislado
- ✅ **Routing por path**: Acceso via `localhost:5007/IDENTIFICADOR/mcp`
- ✅ **Tool personalizada**: Cada instancia tiene `hello_world` con información específica
- ✅ **Gestión automática**: Cleanup y shutdown automáticos
- ✅ **Monitoreo**: Endpoints para health check y lista de instancias

## 🚀 Uso

### Inicio Automático

El servidor dinámico se inicia automáticamente al ejecutar `run.py`:

```python
# En run.py se llama automáticamente
start_dynamic_mcp_with_cleanup()
```

### Conexión a Instancias MCP

```bash
# Conectar a una instancia específica (se crea automáticamente si no existe)
curl http://localhost:5007/mi-identificador/mcp

# Cada identificador tiene su propio servidor MCP aislado
curl http://localhost:5007/ventas/mcp
curl http://localhost:5007/inventario/mcp
curl http://localhost:5007/contabilidad/mcp
```

### Herramienta hello_world

Cada instancia MCP incluye una herramienta `hello_world` personalizada:

```json
{
  "tool": "hello_world",
  "arguments": {}
}
```

**Respuesta de ejemplo:**
```
"Hello! You are connected to mi-identificador MCP! Created 45 seconds ago"
```

## 📊 Endpoints de Monitoreo

### Health Check
```bash
GET http://localhost:5007/health
```
**Respuesta:**
```json
{
  "status": "healthy",
  "active_instances": 3,
  "instances": ["ventas", "inventario", "contabilidad"]
}
```

### Lista de Instancias
```bash
GET http://localhost:5007/instances
```
**Respuesta:**
```json
{
  "active_instances": 2,
  "instances": {
    "ventas": {
      "created_at": "2025-07-18T10:30:00",
      "seconds_alive": 120
    },
    "inventario": {
      "created_at": "2025-07-18T10:32:15",
      "seconds_alive": 45
    }
  }
}
```

## 🛠️ Configuración

### Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `COPILOT_PORT_MCP` | Puerto del servidor dinámico | `5007` |

### Ejemplo de Configuración

```bash
# Configurar puerto personalizado
export COPILOT_PORT_MCP=8080
```

## 🏗️ Arquitectura

### Flujo de Creación Dinámica

1. **Cliente se conecta** a `localhost:5007/IDENTIFICADOR/mcp`
2. **Servidor intercepta** la petición y extrae el identificador
3. **Verifica si existe** una instancia MCP para ese identificador
4. **Crea nueva instancia** si no existe (con herramientas personalizadas)
5. **Redirige la petición** a la instancia MCP correspondiente
6. **Cliente recibe respuesta** de la instancia específica

### Componentes Principales

```
copilot/core/mcp/
├── dynamic_server.py      # Servidor dinámico principal
├── dynamic_manager.py     # Gestor de ciclo de vida
├── dynamic_utils.py       # Utilidades y cleanup
└── README_DYNAMIC.md      # Esta documentación
```

## 🔧 Desarrollo

### Agregar Herramientas Personalizadas

Para cada instancia dinámica puedes agregar herramientas específicas:

```python
# En dynamic_server.py, dentro de DynamicMCPInstance._setup_instance()
@self.app.tool
def mi_herramienta_personalizada(param: str) -> str:
    """Herramienta específica para esta instancia."""
    return f"Procesado en {self.identifier}: {param}"
```

### Testing

Ejecuta el script de prueba incluido:

```bash
python test_dynamic_mcp.py
```

## 🔄 Migración desde Servidor Estático

### Antes (Servidor Estático)
```python
# Un solo servidor MCP para todos
from copilot.core.mcp import start_mcp_with_cleanup
start_mcp_with_cleanup()  # Solo en localhost:5007/
```

### Después (Servidor Dinámico)
```python
# Múltiples servidores MCP bajo demanda
from copilot.core.mcp.dynamic_utils import start_dynamic_mcp_with_cleanup
start_dynamic_mcp_with_cleanup()  # En localhost:5007/IDENTIFICADOR/mcp
```

## 🐛 Troubleshooting

### Puerto en Uso
```bash
# Verificar puerto ocupado
lsof -i :5007

# Cambiar puerto
export COPILOT_PORT_MCP=5008
```

### Verificar Estado
```bash
# Health check
curl http://localhost:5007/health

# Lista de instancias activas
curl http://localhost:5007/instances

# Conectar a instancia específica
curl http://localhost:5007/test/mcp
```

### Logs
```bash
# Ver logs del servidor dinámico
tail -f logs/copilot.log | grep "Dynamic MCP"
```

## 💡 Casos de Uso

1. **Multi-tenant**: Cada cliente tiene su propio servidor MCP
2. **Por departamento**: Ventas, inventario, contabilidad separados
3. **Por proyecto**: Cada proyecto con sus herramientas específicas
4. **Testing**: Crear instancias temporales para pruebas
5. **Desarrollo**: Múltiples versiones de herramientas en paralelo

## 🔒 Seguridad

- ✅ Validación de identificadores (solo alfanuméricos, guiones y guiones bajos)
- ✅ CORS configurado para acceso web seguro
- ✅ Aislamiento entre instancias
- ✅ Cleanup automático en shutdown
