# Dynamic MCP Server - Implementación Completa

## 🎯 Resumen

Este sistema implementa un **Servidor MCP Dinámico** que crea instancias de MCP (Model Context Protocol) bajo demanda basado en identificadores en la URL. En lugar de tener un solo servidor MCP estático, ahora tienes múltiples instancias aisladas que se crean automáticamente cuando alguien se conecta.

## 🚀 Características Principales

- ✅ **Creación bajo demanda**: Las instancias MCP se crean solo cuando son accedidas
- ✅ **Múltiples instancias**: Cada identificador tiene su propia instancia MCP aislada
- ✅ **Asignación automática de puertos**: Cada instancia corre en un puerto diferente
- ✅ **Proxy transparente**: Las peticiones se reenvían automáticamente a la instancia correcta
- ✅ **Tool personalizado**: Cada instancia incluye un tool `hello_world` con información específica
- ✅ **Gestión de ciclo de vida**: Arranque y parada automática de instancias
- ✅ **APIs de monitoreo**: Endpoints para verificar estado y listar instancias activas

## 🌐 Cómo Funciona

### Acceso por URL
```
http://localhost:5007/IDENTIFICADOR/mcp
```

### Ejemplos:
- `http://localhost:5007/company1/mcp` → Crea instancia para "company1"
- `http://localhost:5007/project-alpha/mcp` → Crea instancia para "project-alpha"
- `http://localhost:5007/tenant-123/mcp` → Crea instancia para "tenant-123"

### Arquitectura del Sistema

```
Cliente MCP
    ↓
http://localhost:5007/company1/mcp
    ↓
Servidor Principal FastAPI (puerto 5007)
    ↓ (proxy)
Instancia MCP "company1" (puerto dinámico, ej: 8001)
```

## 📁 Archivos Implementados

### Archivos Principales
- **`simplified_dynamic_server.py`**: Implementación principal del servidor dinámico
- **`simplified_dynamic_manager.py`**: Gestor de ciclo de vida del sistema
- **`simplified_dynamic_utils.py`**: Utilidades y handlers de limpieza
- **`run.py`**: Punto de entrada modificado para usar el sistema dinámico

### Scripts de Prueba
- **`demo_dynamic_mcp.py`**: Demostración completa del sistema
- **`test_server.py`**: Test automatizado con arranque/parada del servidor
- **`simple_test.py`**: Test básico para verificar funcionalidad

## 🛠️ Uso

### 1. Iniciar el Servidor
```bash
python run.py
```

### 2. Verificar Estado
```bash
curl http://localhost:5007/health
```

### 3. Crear Instancia MCP
```bash
curl http://localhost:5007/mi-empresa/mcp
```

### 4. Listar Instancias Activas
```bash
curl http://localhost:5007/instances
```

### 5. Ejecutar Demo
```bash
python demo_dynamic_mcp.py
```

## 🔧 API Endpoints

### Servidor Principal (puerto 5007)

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/` | GET | Información del servidor |
| `/health` | GET | Estado de salud |
| `/instances` | GET | Lista de instancias activas |
| `/{id}/mcp` | GET/POST | Acceso a instancia MCP (crea bajo demanda) |
| `/{id}/mcp/{path}` | GET/POST | Rutas adicionales de la instancia MCP |

### Respuesta de `/instances`
```json
{
  "active_instances": 2,
  "instances": {
    "company1": {
      "created_at": "2024-01-15T10:30:00",
      "seconds_alive": 45,
      "port": 8001,
      "url": "http://localhost:8001",
      "status": "running"
    },
    "project-alpha": {
      "created_at": "2024-01-15T10:30:30",
      "seconds_alive": 15,
      "port": 8002,
      "url": "http://localhost:8002",
      "status": "running"
    }
  }
}
```

## 🧪 Tools Disponibles

Cada instancia MCP incluye:

- **`hello_world`**: Saludo personalizado con información de la instancia
- **`ping`**: Test básico de conectividad
- **`server_info`**: Información del servidor
- **`init_session`**: Inicialización de sesión
- **`agent_greeting`**: Saludo del agente
- **`get_agent_info`**: Información del agente

### Ejemplo del Tool hello_world
```
Input: Acceso a http://localhost:5007/company1/mcp
Tool: hello_world()
Output: "Hello! You are connected to company1 MCP! Created 42 seconds ago."
```

## 🔒 Validación de Identificadores

Los identificadores deben ser:
- Alfanuméricos
- Pueden incluir guiones (`-`) y guiones bajos (`_`)
- No pueden contener espacios o caracteres especiales

### Válidos: ✅
- `company1`
- `project-alpha`
- `tenant_123`
- `dev-environment`

### Inválidos: ❌
- `company 1` (espacio)
- `project@alpha` (carácter especial)
- `tenant#123` (carácter especial)

## 🎭 Demo en Acción

Ejecuta el script de demostración para ver el sistema funcionando:

```bash
# Terminal 1: Iniciar servidor
python run.py

# Terminal 2: Ejecutar demo
python demo_dynamic_mcp.py
```

La demo creará varias instancias y mostrará:
1. Estado inicial (0 instancias)
2. Creación de "company1"
3. Creación de "company2"
4. Creación de "project-alpha"
5. Lista de todas las instancias activas
6. Reutilización de instancia existente

## 🏗️ Arquitectura Técnica

### Componentes Principales

1. **SimplifiedDynamicMCPServer**: Servidor FastAPI principal que actúa como proxy
2. **DynamicMCPInstance**: Representa una instancia MCP individual
3. **SimplifiedDynamicMCPManager**: Gestiona el ciclo de vida del sistema
4. **Handlers de limpieza**: Aseguran el cierre correcto de todas las instancias

### Flujo de Creación de Instancia

1. Cliente accede a `/company1/mcp`
2. Servidor verifica si existe instancia para "company1"
3. Si no existe:
   - Crea `DynamicMCPInstance("company1")`
   - Encuentra puerto libre (ej: 8001)
   - Inicia servidor uvicorn en ese puerto
   - Registra tools personalizados
4. Proxy la petición al puerto de la instancia
5. Devuelve respuesta al cliente

### Gestión de Puertos

- **Puerto principal**: 5007 (configurable)
- **Puertos de instancias**: 8000+ (detección automática)
- **Algoritmo**: Busca primer puerto libre desde 8000
- **Prevención de conflictos**: Cada instancia usa puerto único

## 🧹 Limpieza y Recursos

El sistema incluye handlers automáticos para:
- **SIGTERM/SIGINT**: Cierre correcto al terminar aplicación
- **Limpieza de puertos**: Liberación automática de puertos
- **Tareas asyncio**: Cancelación de tareas en background
- **Recursos de red**: Cierre de conexiones HTTP

## 📊 Monitoreo y Debugging

### Logs
El sistema genera logs detallados:
```
INFO: Creating new MCP instance for identifier: company1
INFO: MCP instance for company1 created and started on port 8001
DEBUG: Using existing MCP instance for identifier: company1
```

### Debugging
- Usa `/health` para verificar que el servidor principal está activo
- Usa `/instances` para ver todas las instancias y sus puertos
- Accede directamente a puertos de instancia para debugging (ej: `http://localhost:8001/`)

## ✅ Beneficios del Sistema

1. **Escalabilidad**: Cada cliente/tenant tiene su MCP aislado
2. **Eficiencia**: Solo se crean instancias cuando se necesitan
3. **Aislamiento**: Problemas en una instancia no afectan otras
4. **Flexibilidad**: Fácil agregar nuevos tools específicos por instancia
5. **Monitoreo**: Visibilidad completa de instancias activas
6. **Recursos**: Optimización automática de uso de memoria y CPU

## 🎉 ¡Listo para Usar!

Tu sistema MCP dinámico está completo y funcionando. Cada vez que alguien acceda a una URL como `http://localhost:5007/mi-empresa/mcp`, se creará automáticamente una nueva instancia MCP dedicada con su propio tool `hello_world` que dirá:

> "Hello! You are connected to mi-empresa MCP! Created X seconds ago."

¡El sueño de tener múltiples MCPs en uno solo ahora es realidad! 🚀
