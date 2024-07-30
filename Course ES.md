# Introduccion a Etendo Copilot
## Que es un LLM?
LLM (Large Language Model) es un modelo de lenguaje de gran escala entrenado en enormes cantidades de datos textuales. Utiliza técnicas de aprendizaje automático, particularmente redes neuronales profundas, para procesar y generar texto de manera que imite el lenguaje humano. Estos modelos son capaces de:

	1.	Comprender el contexto y el significado de las palabras y frases.
	2.	Generar texto coherente y relevante en respuesta a entradas dadas.
	3.	Realizar tareas diversas como traducción, resumen, redacción de textos, y más.


### ¿Quién es OpenAI?

OpenAI es una organización de investigación en inteligencia artificial fundada en diciembre de 2015 por Elon Musk, Sam Altman, Greg Brockman, Ilya Sutskever, John Schulman y Wojciech Zaremba. La misión de OpenAI es asegurar que la inteligencia artificial general (AGI) beneficie a toda la humanidad.

### ¿Cómo revolucionó OpenAI el mundo con sus modelos de lenguaje?

OpenAI revolucionó el campo de la inteligencia artificial con el desarrollo de modelos de lenguaje avanzados, entre los cuales destacan:

	1.	GPT (Generative Pre-trained Transformer):
	•	GPT-2 (2019): Un modelo capaz de generar texto sorprendentemente coherente y contextualizado, aunque sus capacidades y potencial mal uso causaron preocupaciones significativas sobre la ética y la seguridad en la IA.
	•	GPT-3 (2020): Con 175 mil millones de parámetros, GPT-3 es uno de los modelos más grandes y avanzados jamás creados, capaz de realizar tareas de lenguaje natural con alta precisión y creatividad.
	•	GPT-4 (2023): La versión más avanzada que sigue mejorando en comprensión, generación y tareas específicas de lenguaje.

### ¿Por qué son útiles los LLMs para realizar muchas tareas?

Los LLMs son útiles porque pueden:

	1.	Automatizar tareas repetitivas y tediosas: Como redacción de informes, generación de resúmenes, y respuestas automáticas en servicios de atención al cliente.
	2.	Asistir en la creatividad: Ayudando a escritores, artistas y desarrolladores a generar ideas, textos y guiones.
	3.	Mejorar la accesibilidad: Traduciendo textos en tiempo real, proporcionando subtítulos, y generando contenido accesible para personas con discapacidades.
	4.	Procesar y analizar datos: Facilitando el análisis de grandes volúmenes de texto, identificación de patrones y extracción de información relevante.
	5.	Personalizar experiencias: Creando contenido personalizado basado en las preferencias y comportamientos del usuario.

Estos modelos han transformado diversas industrias, incluyendo la tecnología, educación, salud, y entretenimiento, al proporcionar herramientas avanzadas que mejoran la eficiencia, la creatividad y la accesibilidad.

### Como un modelo orientado a completar texto paso a ser un asistente que ayuda a los usuarios a realizar tareas?
Inicialmente, la inteligencia artificial (IA) desarrollada por OpenAI se enfocaba en continuar textos de manera coherente, lo que ya era un logro impresionante. Sin embargo, a medida que estos modelos de lenguaje, como GPT-3 y GPT-4, se fueron perfeccionando, su capacidad para comprender y generar texto se tradujo en la habilidad para resolver tareas específicas. Al entrenarse con datos masivos y variados, estos modelos adquirieron competencias en múltiples dominios, desde responder preguntas complejas, redactar ensayos, traducir entre idiomas, hasta realizar análisis de sentimientos y programación de código. Esta evolución permitió que la IA pasara de ser una simple herramienta de autocompletado a convertirse en un asistente versátil y potente, capaz de abordar problemas complejos y realizar una amplia gama de tareas con precisión y eficiencia.

## Que es Etendo Copilot?

Copilot es un modulo para Etendo Classic, el cual provee una plataforma para crear y administrar asistentes personalizados, los cuales funcionan basandose en modelos LLMs avanzados. Estos asistentes pueden ser utilizados para realizar tareas específicas, responder preguntas, generar contenido, y mucho más. Copilot permite a los usuarios aprovechar el poder de la inteligencia artificial para mejorar su productividad, creatividad y eficiencia en diversas áreas. El modulo de Etendo Copilot permite personalizar el comportamiento de los asistentes, alimentarlos con datos específicos, y desarrollar herramientas adicionales para extender sus capacidades permitiendo que el asistente realice acciones. Estas funcionalidades se las denomina "tools" y permiten a los asistentes interactuar con otros sistemas, realizar tareas automatizadas, y brindar una experiencia más completa y personalizada a los usuarios.

## Que contiene el modulo de Etendo Copilot?

### Ventanas de Etendo Copilot en Etendo Classic
- **Asistentes**: Muestra una lista de los asistentes disponibles, permitiendo crear, editar, y administrar asistentes personalizados.
- **Tools**: Permite desarrollar y administrar herramientas adicionales que extienden las capacidades de los asistentes, permitiendo interactuar con otros sistemas y realizar tareas automatizadas.
- **Assistant Access**: Permite configurar los permisos de acceso y uso de los asistentes, definiendo quién puede interactuar con ellos y qué acciones pueden realizar.
- **Knowledge Base File**: Permite añadir y administrar documentos y datos que alimentan la base de conocimiento de los asistentes, mejorando su capacidad para responder preguntas y realizar tareas.

### Copilot Chat
Con el modulo de Copilot, se incluye una interfaz de chat integrada en Etendo Classic, que permite interactuar con los asistentes a los que el usuario tiene acceso. Esta interfaz proporciona una forma intuitiva y eficiente de comunicarse con los asistentes, realizar consultas, y recibir respuestas en tiempo real.

### Copilot API
Además de la interfaz de chat, Copilot proporciona una API que permite a los desarrolladores integrar los asistentes en otros sistemas y aplicaciones. Esta API permite interactuar con los asistentes, enviar consultas, recibir respuestas, y realizar acciones específicas a través de una interfaz programática.

### LangGraph
Debido a la especialización de los asistentes y la diversidad de tareas que pueden realizar, Copilot incluye un tipo de asistente especializado de tipo "LangGraph", el cual esta basado en la libreria homonima. Este tipo de asistente actua como "Supervisor" de otros asistentes, formando equipos de trabajo (estos asistentes se denominan "Team Members") que colaboran para resolver tareas complejas y especializadas. De esta manera se pueden tener varios asistentes que cumplan tareas especificas y el usuario puede interacturar con el supervisor, haciendo el mismo las distribuciones de tareas entre los miembros del equipo.

## Por que y cuando usar Etendo Copilot?
Etendo Copilot es una herramienta poderosa y versátil que puede ser utilizada en una amplia variedad de contextos y situaciones. Algunas de las razones por las que se puede utilizar Etendo Copilot incluyen:

  1.	Automatización de tareas repetitivas: Copilot puede realizar tareas tediosas y repetitivas de manera eficiente y precisa, liberando a los usuarios de trabajos monótonos y permitiéndoles centrarse en tareas más creativas y estratégicas.
  2.	Asistencia en la toma de decisiones: Copilot puede proporcionar información relevante, análisis de datos, y recomendaciones basadas en modelos avanzados, ayudando a los usuarios a tomar decisiones informadas y acertadas.
  3.	Generación de contenido: Copilot puede redactar textos, informes, y documentos de manera rápida y efectiva, ahorrando tiempo y esfuerzo en la creación de contenido.
  4.	Aprovechar desarrollos genericos: Copilot puede ser utilizado para implementar soluciones genericas y reutilizables en diferentes contextos, mejorando la eficiencia y la consistencia en la realización de tareas. Al poder explicarle a un asistente como realizar una tarea, se puede aprovechar una herramienta para usarla en diferentes contextos.
  5.	Interacción con otros sistemas: Copilot puede interactuar con otros sistemas y aplicaciones a través de herramientas personalizadas, permitiendo la integración y automatización de procesos complejos y heterogéneos.

En resumen, Etendo Copilot puede ser muy util en tareas que no tengan una solucion directa, sino que requieran de un razonamiento y procesamiento de informacion para llegar a una solucion.

# Funcionamiento de Etendo Copilot
Como comentamos anteriormente, Etendo Copilot es un modulo para Etendo Classic, por lo que su instalacion es igual que cualquier otro modulo.
Una vez instalado, se podra acceder a las ventanas de Copilot desde el menu de Etendo Classic, donde se podra crear, editar, y administrar asistentes y tools. Para poder utilizar Copilot, es necesario levantar un servicio mediante un comando en Gradle, usando el plugin de Gradle de Etendo. Este Servicio es el que se encarga de comunicar Etendo Classic con los proveedores de LLMs (como OpenAI) y de gestionar las interacciones con los asistentes y tools. Este servicio se encuentra Dockerizado, por lo que es necesario tener Docker instalado en la maquina donde se quiera levantar el servicio.
El comando para iniciar el servicio de Copilot es el siguiente:
``` bash
./gradlew copilot.start --info
```
Este comando levantara el servicio de Copilot. Puede tardar unos minutos en levantarse, ya que necesita descargar las imagenes de Docker y configurar el entorno. 

``` mermaid

sequenceDiagram
    Pop-up->>Etendo Classic: Question + Assistant ID
    Etendo Classic->>Copilot (Docker): Question + Context + Assistant Info
    Copilot (Docker)->>OpenAI(Or Other provider): LLM Calls 
    OpenAI(Or Other provider)->>+Copilot (Docker): LLM Tool call request
    Copilot (Docker)-->>Etendo Classic: Event: Tool Call
    Etendo Classic-->>Pop-up: Show Tool Call
    Copilot (Docker)->>-OpenAI(Or Other provider): Tool Execution response
    OpenAI(Or Other provider)->>+Copilot (Docker): LLM Tool call request
    Copilot (Docker)-->>Etendo Classic: Event: Tool Call
    Etendo Classic-->>Pop-up: Show Tool Call
    Copilot (Docker)->>-OpenAI(Or Other provider): Tool Execution response
    OpenAI(Or Other provider)->>Copilot (Docker): Final Answer
    Copilot (Docker)-->>Etendo Classic: Event: Final answer
    Etendo Classic-->>Pop-up: Show Final answer
```


## Configuracion de Copilot
Copilot requiere ciertas configuraciones basicas, las cuales se tienen que realizar en el archivo ```gradle.properties``` de Etendo. A su vez, estas tambien tienen que estar reflejadas en el archivo ```config/Openbravo.properties```. Estas configuraciones seran usadas tanto por Etendo Classic para comunicarse con Copilot, como por Copilot para funcionar y comunicarse con los proveedores de LLMs. El contenedor de Docker de Copilot usara estas configuraciones como variables de entorno. Las configuraciones basicas que se tienen que realizar son:


| Propiedad | Descripcion | Valor por defecto | Oblicatoriedad |
| --- | --- | --- | --- |
| OPENAI_API_KEY  | Clave de API de OpenAI utilizada por Etendo Copilot | sk-proj-********* | Sí |
|COPILOT_HOST|Host utilizado en Etendo classic para comunicarse con Copilot | localhost | No |
| COPILOT_PORT  | Puerto utilizado por el servicio de Etendo Copilot  | 5005  | No |
| AGENT_TYPE  | Tipo de agente por defecto en Etendo Copilot (*)| langchain | No |
| CONFIGURED_TOOLS_FILENAME | Nombre del archivo de configuración de herramientas utilizado por Etendo Copilot| tools_config.json | No  |
| DEPENDENCIES_TOOLS_FILENAME | Nombre del archivo de dependencias de herramientas utilizado por Etendo Copilot| tools_deps.toml | No |
| OPENAI_MODEL  | Modelo de lenguaje de OpenAI utilizado por Etendo Copilot por defecto.(*) | gpt-4o  | No |
| SYSTEM_PROMPT | Texto de inicio del sistema utilizado por Etendo Copilot. (*) | You are very powerful assistant, that can use a set of tools to do a lot of things. You can use the output of one tool as the input of another tool. So, you can create a chain of tools to solve complex problems | Sí |
| LANGCHAIN_TRACING_V2  | Indicador de trazado de LangChain utilizado por Etendo Copilot  | False  | No |
| LANGCHAIN_ENDPOINT  | Punto final de LangChain utilizado por Etendo Copilot | https://api.smith.langchain.com | No |
| LANGCHAIN_API_KEY | Clave de API de LangChain utilizada por Etendo Copilot  | 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 | No |
| LANGCHAIN_PROJECT | Proyecto de LangChain utilizado por Etendo Copilot  | LocalValentin | No |
| COPILOT_DEBUG | Indicador de depuración de Etendo Copilot, esto indica a Copilot que imprima en consola informacion de debug. | False  | No |
| ETENDO_HOST | URL de Etendo Classic utilizado por Etendo Copilot, para comunicarse con Classic (**)  | http://host.docker.internal:8080 | No |

- (*) Estos valores son obligatiorios desde la configuracion de Asistentes en Etendo Classic, pero pueden ser opcionales mediante el uso de la API de Copilot.
- (**) Este valor aplicara si se esta utilizando Docker para levantar Etendo Classic y Copilot en la misma maquina. Si se esta utilizando Docker para levantar Etendo Classic y Copilot en maquinas diferentes, se tiene que cambiar el valor de esta propiedad por la IP o Dominio de la maquina donde se encuentra Etendo Classic. Es recomendable siempre intentar usar el dominio si se trata de un servidor.

Algunas tools pueden requerir variables de ambiente adicionales, las cuales deben configurarse en el archivo ```gradle.properties``` de Etendo y en el archivo ```config/Openbravo.properties```. 

## Que es un asistente?
Un asistente de Copilot es un agente de inteligencia artificial especializado en realizar tareas específicas, responder preguntas, y generar contenido relevante. Los asistentes de Copilot se basan en modelos de lenguaje avanzados, como GPT-4o, que les permiten comprender el contexto, el significado y las intenciones del usuario, y generar respuestas coherentes y precisas en función de la entrada dada. Los asistentes pueden ser personalizados y entrenados para adaptarse a las necesidades y preferencias del usuario, mejorando su capacidad para realizar tareas específicas y brindar una experiencia personalizada. Dado que los modelos de lenguaje tienen conocimiento previo de todo tipo (hasta la fecha de su entrenamiento), los asistentes pueden responder a una amplia variedad de preguntas y realizar tareas diversas, desde redacción de textos, traducción de idiomas, análisis de datos, hasta programación de código. Lo mas problable es que el asistente no tenga conocimiento de eventos que hayan ocurrido despues de su fecha de entrenamiento o conocimientos muy especificos que no hayan sido incluidos en su entrenamiento. Ademas, no tienen un comportamiento deterministico, por lo que pueden dar respuestas diferentes a la misma pregunta en diferentes ocasiones, o hacer distintas asunciones en base a la misma pregunta. Esto plantea la necesidad de especificar un prompt para indicarle al asistente contexto sobre las tareas, que informacion puede recibir, que tipo de respuesta se espera y que flujos de trabajo se deben seguir, esto permitiendo modelar el comportamiento del asistente. Finalmente, para que los asistentes puedan obtener conocimientos por fuera de su entrenamiento inicial, se pueden alimentar con documentos y datos adicionales, los cuales se almacenan en la base de conocimiento del asistente. Estos documentos pueden ser añadidos y administrados desde la ventana de "Knowledge Base File" en Etendo Classic.

## Que es una tool?
Inicialmente, los LLM solo pueden retornar informacion, ya sea de su entrenamiento o de la informacion que se le provee en el prompt. Sin embargo, en muchas ocasiones, se necesita que el asistente realice acciones, como interactuar con otros sistemas, realizar tareas automatizadas, o procesar datos de manera específica. Para esto, se pueden desarrollar herramientas adicionales que extienden las capacidades de los asistentes, permitiéndoles realizar acciones y tareas más complejas. Estas herramientas se denominan "tools" y se pueden desarrollar y administrar desde la ventana de "Tools" en Etendo Classic. Las tools son scripts de Python que definen una serie de acciones y tareas que el asistente puede realizar. Estas acciones pueden incluir desde la interacción con APIs externas, el procesamiento de datos, la generación de contenido, hasta la ejecución de comandos en sistemas externos. 
### Cual es la utilidad de las tools?
Las tools permiten a los asistentes realizar tareas específicas y personalizadas, adaptándose a las necesidades y preferencias del usuario. Las tools pueden ser desarrolladas por los usuarios o por terceros, y pueden ser compartidas y reutilizadas en diferentes contextos y situaciones. Las tools se pueden añadir a los asistentes para extender sus capacidades y mejorar su funcionalidad, permitiendo a los usuarios realizar tareas más avanzadas y especializadas. La premisa de las tools es que el asistente las puede utilizar estrategicamente para realizar tareas, es decir, el asistente puede usar la salida de una tool como la entrada de otra tool, permitiendo crear flujos de trabajo complejos y especializados. 
### Que beneficio aportan las tools a los asistentes? Tienen que ser complejas?
Es mejor tener tools pequeñas y un prompt bien explicado que una tool muy grande y compleja. Las tools deben ser lo más granulares y genericas posibles, para que puedan ser reutilizadas en diferentes contextos y situaciones.
Por ejemplo, si necesitamos un asistente para que descargue un archivo de una URL, lo resuma y luego lo suba a un sistema externo, se pueden crear tres tools, una para descargar el archivo, otra para resumirlo y otra para subirlo, y el asistente puede usar estas tools en secuencia para realizar la tarea. Entonces, dado este ejemplo, si le agregamos otra tool que le permita al asistente enviar un mail, podriamos ser capaces de solicitarle que en vez de subir el archivo a un sistema externo, lo envie por mail. De esta manera, con tools lo suficientemente "granulares", se pueden realizar tareas complejas y especializadas. Ademas, el asistente se puede adaptar a situaciones no previstas.



## Como se interactua con un asistente?
Los asistentes de Copilot pueden ser interactuados de diferentes maneras, dependiendo de las necesidades y preferencias del usuario. Algunas de las formas comunes de interactuar con un asistente incluyen:

  1.	Interfaz de chat: Copilot proporciona una interfaz de chat integrada en Etendo Classic, que permite al usuario enviar consultas, recibir respuestas, y realizar tareas a través de una conversación en tiempo real. Esta interfaz es intuitiva y eficiente, y permite al usuario comunicarse con el asistente de manera natural y fluida. La interfaz consiste de un selector
de asistentes en la parte superior, el cual permite seleccionar el asistente con el que se quiere interactuar, solo mostrando los asistentes a los que el usuario tiene acceso. En la parte inferior, se encuentra el chat, donde se puede enviar mensajes al asistente y recibir respuestas. Al hacer una peticion al asistente, un icono que indica si se esta ejecutando alguna tool o procesando la informacion. Adicionalmente, si estamos interactuando con un asistente LangGraph, se podra ver un icono que indica con que asistente del equipo se esta interactuando.

  2.	API de Copilot: Además de la interfaz de chat, Copilot proporciona una API que permite a los desarrolladores integrar los asistentes en otros sistemas y aplicaciones. Esta API permite enviar mensajes y recibir respuestas a un asistente.


![image](https://PlaceHolder)


# Desarrollo con Etendo Copilot
Etendo Copilot proporciona una plataforma para desarrollar asistentes personalizados y herramientas adicionales que extienden las capacidades de los asistentes. El desarrollo con Copilot implica la creación, edición, y administración de asistentes y tools, así como la alimentación de datos y documentos en la base de conocimiento de los asistentes. Copilot aprovecha la modularidad de Etendo Classic, permitiendo a los desarrolladores personalizar los asistentes y las tools, permitiendo que estos modulos agreguen tools y almacenen, si se requiere, la configuracion de los asistentes.

## Estrucutra de modulos en Etendo Copilot
![image](https://PlaceHolder)
- Carpeta Tools: Contiene las tools desarrolladas por el usuario. Cada Tool es un script de Python que implenta una interfaz definida por Copilot. Las tools pueden ser desarrolladas y administradas desde la ventana de "Tools" en Etendo Classic.
- Carpeta tests: Contiene tests unitarios para las tools. Estos tests son necesarios para garantizar el correcto funcionamiento de las tools y para detectar posibles errores o fallos en su implementación.
- Archivo tool_deps.toml: Contiene las dependencias de las tools. Este archivo es utilizado por Copilot para instalar las dependencias necesarias para ejecutar las tools.
- Archivo build.gradle: Contiene la configuración de Gradle para el modulo de Copilot. Este archivo es necesario para que Copilot reconozca el modulo.
- Archivos de diccionario de aplicacion: Contienen la informacion de los asistentes, en caso de tener asistentes exportados en el modulo. Tambien, deben contener la informacion de las tools, ya que, deben registrarse en el diccionario de aplicacion para que Copilot las reconozca.

!info
  Al iniciar el servicio de Copilot, este escanea los modulos de classic y recopila los archivos Python de las tools. Ademas de esto, tambien recopila archivos de dependencias de las tools, para luego instalarlas dentro de su contenedor de Docker.

## Como crear un asistente?

Desde la ventana "Asisstant" podremos crear, editar, eliminar asistentes. Tenemos datos obligatorios y opcionales que debemos completar para poder crear un asistente. Los datos obligatorios son:
- Nombre del asistente: Nombre que identifica al asistente.
- Descripcion del asistente: Breve descripcion del asistente, que puede hacer o en que tarea puede ayudar.
- Prompt: Texto que define contexto, personalidad y comportamiento del asistente. Este texto es muy importante, ya que le indica al asistente que tipo de informacion puede recibir, que tipo de respuesta se espera y que flujos de trabajo se deben seguir. Este texto es el que modela el comportamiento del asistente.
- Tipo de asistente: Tipo de asistente que se esta creando. Los tipos de asistentes son:
  - LangChain: Asistente basado en la libreria LangChain, que proveen su propia API para la creacion de asistentes.
  - OpenAI Assistant: Asistente basado en la API de Assistans de OpenAI, que utiliza modelos de lenguaje avanzados para responder preguntas y realizar tareas específicas.
  - LangGraph: Asistente especializado en coordinar y supervisar otros asistentes, formando equipos de trabajo que colaboran para resolver tareas complejas y especializadas.

  | Info |
  | ------------- | 
  |  En general, los asistentes **LangChain y OpenAI Assistant** son los mas **sencillos y son casi equivalentes**, ya que ambos utilizan la API de OpenAI para responder preguntas y realizar tareas. La principal diferencia es que los asistentes **LangChain no sube los archivos de la base de conocimiento a OpenAI**, sino que los mantiene en su propio servidor. Los asistentes **LangGraph** son mas complejos, ya que **coordinan y supervisan otros asistentes**, armando un grafo de asistentes que colaboran para resolver tareas complejas y especializadas.|

Los datos opcionales son:
- Proveedor de LLM: Proveedor de LLM que se utilizara para el asistente. Si no se especifica, se utilizara el proveedor por defecto.
- Modelo de lenguaje: Modelo de lenguaje en especifico que se utilizara para el asistente. Si no se especifica, se utilizara el modelo por defecto, definido en base al proveedor de LLM.
- Temperatura: Es un numero entre 0 y 2, que indica que tan creativo o arriesgado es el asistente. Un valor de 0 indica que el asistente es muy conservador y se apega a lo que sabe, mientras que un valor de 2 indica que el asistente es muy creativo y arriesgado. Si no se especifica, se utilizara la temperatura por defecto.

## Como añadir documentos a la base de conocimiento de un asistente?
Para añadir documentos propios a la base de conocimiento de un asistente, se debe acceder a la ventana "Knowledge Base File" en Etendo Classic. Desde esta ventana, se pueden añadir, editar, y eliminar documentos que alimentan la base de conocimiento del asistente. Los documentos pueden ser de diferentes tipos segun su origen:
- HQL: Documentos generados a partir de consultas HQL en Etendo Classic. Esta se ejecuta cuando sea solicitado por el asistente, ya sea en su sincronizacion o en su ejecucion.
- Attached File: Documento adjunto que se sube al asistente. Este documento se almacena como attachment tradicional de Classic y se sincroniza con el asistente.
- Remote File: Documento que se descarga de una URL. Este documento se descarga cuando el asistente lo solicita, ya sea en su sincronizacion o en su ejecucion.

Una vez añadidos los documentos en su ventana, deben ser enlazados al asistente desde la ventana de "Assistants". Para esto, se debe seleccionar el asistente al que se le quiere añadir el documento, y en la pestaña de "Knowledge Base", se debe seleccionar el documento que se quiere añadir. Es necesario especificar que tipo de adjunto es, en otra palabras, de como se integra al documento en la base de conocimiento del asistente:
- Add to Knowledge Base: Añade el documento a la base de conocimiento del asistente, permitiendo que el asistente lo utilice para responder preguntas y realizar tareas.
- Append to system Prompt: Añade el contenido del documento al prompt del asistente, permitiendo que el asistente lo utilice para determinar su comportamiento y personalidad. Esto se realiza a la hora de sincronizar el asistente (OpenAI Assistant) o a la hora de hacer la primera consulta al asistente (LangChain).
- Append to question: Añade el contenido del documento a la pregunta que se le hace al asistente, esto siendo util cuando se requiere un contexto del momento en que se realiza la consulta. Un ejemplo de esto es la fecha y hora actual, o el clima en el momento de la consulta.

| Info |
| ------------- |
| Es importante siempre hacer una sincronizacion del asistente luego de añadir documentos a la base de conocimiento, para que el asistente pueda utilizarlos en sus respuestas.|

## Como configurar/desarrollar una tool para el asistente?

### Como agregar una tool al asistente?
Para agregar una tool al asistente, disponemos de la pestaña "Skill/Tool", en la que se pueden enlasar las tools que se quieren añadir al asistente. Los modulos de "Copilot Extensions" disponen de varios modulos que incluyen tools de propositos generales que pueden ser utilizadas en diferentes contextos. 

### Como desarrollar una tool para el asistente?
Para desarrollar una tool para el asistente, se debe crear un modulo en Etendo Classic que contenga la tool (si es necesario).
Las tools son funciones Python programaticas, que siguen un formato determinado. Es recomendable crear tests unitarios para las tools, que permitan garantizar su correcto funcionamiento y detectar posibles errores o fallos en su implementación y ejecucion.
Haremos un ejemplo de una tool llamada MeanAndStdDev, que calcula la media y la desviacion estandar de una lista de numeros. Supongamos que ya tenemos la siguiente funcion en Python que calcula la media y la desviacion estandar de una lista de numeros:
``` python
import numpy as np

def calculate_mean_and_std(numbers1, numbers2):
    """
    Esta función toma dos listas de números, las convierte en arrays de numpy,
    y luego calcula la media y la desviación estándar de los números combinados.

    Parámetros:
    numbers1 (list): La primera lista de números.
    numbers2 (list): La segunda lista de números.

    Retorna:
    tuple: Una tupla con la media y la desviación estándar de los números combinados.
    """
    array1 = np.array(numbers1)
    array2 = np.array(numbers2)
    combined_array = np.concatenate((array1, array2))
    mean = np.mean(combined_array)
    std_dev = np.std(combined_array)
    return mean, std_dev
```
Para crear la tool en Etendo Classic, se debe seguir el siguiente formato:
``` python
from typing import Type, Dict

from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper, ToolOutput, ToolOutputMessage

class CustomToolInput(ToolInput):
    param1: str = ToolField(description="Description of the first parameter")
    param2: str = ToolField(description="Description of the second parameter")
    # Add more parameters as needed

class CustomTool(ToolWrapper):
    """A tool to perform a specific task.

    Parameters:
    - param1 (str): Description of the first parameter.
    - param2 (str): Description of the second parameter.
    """

    name = "CustomTool"
    description = "Description of what the tool does."
    args_schema: Type[ToolInput] = CustomToolInput
    return_direct: bool = False

    def run(self, input_params: Dict = None, *args, **kwargs) -> ToolOutput:
        # Import necessary libraries
        # import numpy as np

        # Retrieve input parameters
        param1 = input_params["param1"]
        param2 = input_params["param2"]
        
        # Implement the tool's logic here
        # Example: Perform some calculations or operations
        # result = some_function(param1, param2)
        
        # Create the response message
        response = f"Result of the operation with {param1} and {param2}"

        return ToolOutputMessage(message=response)
```
Como podemos ver, la tool se compone de dos clases: La que define los parametros de entrada de la tool y la que define la tool en si. La clase que define los parametros de entrada de la tool debe heredar de la clase ToolInput y definir los parametros de entrada de la tool. 
La clase que define la tool en si debe heredar de la clase ToolWrapper y definir los parametros de la tool, la logica de la tool, y el mensaje de salida de la tool. En este caso, la tool recibe dos parametros de entrada, param1 y param2, y devuelve un mensaje con el resultado de la operacion realizada con estos parametros.

El resultado de aplicar el formato de tool a la funcion de Python que calcula la media y la desviacion estandar de una lista de numeros seria el siguiente:

``` python
from typing import Type, Dict

from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper, ToolOutput, ToolOutputMessage


class MeanAndStdInput(ToolInput):
    numbers1: str = ToolField(description="First list of numbers, comma-separated")
    numbers2: str = ToolField(description="Second list of numbers, comma-separated")
    messageToUser: str = ToolField(description="Message to the user", required=False)

class MeanAndStdTool(ToolWrapper):
    """A tool to calculate the mean and standard deviation of two lists of numbers.

    Parameters:
    - numbers1 (str): The first list of numbers, comma-separated.
    - numbers2 (str): The second list of numbers, comma-separated.
    """

    name = "MeanAndStdTool"
    description = "Calculates the mean and standard deviation of two lists of numbers."
    args_schema: Type[ToolInput] = MeanAndStdInput
    return_direct: bool = False

    def run(self, input_params: Dict = None, *args, **kwargs) -> ToolOutput:
        import numpy as np
        numbers1 = input_params["numbers1"]
        numbers2 = input_params["numbers2"]
        numbers1_list = list(map(float, numbers1.split(',')))
        numbers2_list = list(map(float, numbers2.split(',')))
        array1 = np.array(numbers1_list)
        array2 = np.array(numbers2_list)
        combined_array = np.concatenate((array1, array2))
        mean = np.mean(combined_array)
        std_dev = np.std(combined_array)
        response = f"Mean: {mean}, Standard Deviation: {std_dev}"
        return ToolOutputMessage(message=response)
```
Un detalle no menor, es que en este caso, la tool utiliza la libreria numpy, por lo que es necesario importarla en la tool. Este tipo de librerias, al ser externas y no incluida dentro del Copilot base, tenemos que importarlas dentro del metodo run de la tool, o sea, al momento de ejecutar la tool. Para que Copilot al iniciar el servicio pueda instalar las dependencias necesarias para la ejecucion de las tools, se debe especificar en el archivo tool_deps.toml las dependencias necesarias para la ejecucion de la tool. En este caso, la tool MeanAndStdTool necesita la libreria numpy, por lo que el archivo tool_deps.toml seria el siguiente:

``` toml
[MeanAndStdTool]
numpy = "1.21.2" # Version de numpy que se necesita, o puede ser *
```
Si reiniciamos Copilot, mirando en su log, deberiamos ver que instala la libreria numpy, y que carga la tool MeanAndStdTool.

### Como registrar una tool en el diccionario de aplicacion?
Una vez creamos el archivo de la tool, debemos registrarla en el diccionario de aplicacion de Etendo Classic. Esto es necesario para que Copilot reconozca la tool y para poder configurarla en los asistentes para que lo utilicen.
Debemos ir a la ventana "Skill/Tool" en Etendo Classic como "System Administrator".
Una vez ahi, debemos crear un nuevo registro, siendo el searchkey el Nombre del archivo de la tool, y en "Name" el nombre completo de la Tool. Por ultimo, debe ejecutar boton "Sync Tool" para que la tool sea leida y el proceso almacene en Etendo la estructura de la Tool y su descripcion. Este proceso permite obtener/actualizar la metadata de la Tool, la cual es utilizada por el asistente para "entenderla". Se debe indicar el modulo al que pertenece la tool, para luego exportar estos registros.

Finalmente, podremos configurar la tool en los asistentes que las necesiten. Es importante sincronizar el asistente luego de añadir la tool, para que el asistente pueda utilizarla en sus respuestas.

| Info |
| ------------- |
| Es muy importante que el nombre del archivo de la tool, el nombre de la clase de la tool, el nombre especificado en el archivo de la tool_deps.toml y el searchkey del registro en el diccionario de aplicacion sean iguales, ya que Copilot utiliza estos nombres para reconocer la tool, dado que es su identificador unico.|

## Como configurar un asistente LangGraph?
Para configurar un asistente LangGraph, se debe seguir los mismos pasos que para configurar un asistente normal, con la diferencia de que se debe seleccionar el tipo de asistente "LangGraph" en la ventana de "Asistants". 
Ademas, se debe añadir los asistentes que formaran parte del equipo de trabajo del asistente LangGraph, en la pestaña "Team Members" de la ventana de "Asistants". El asistente de tipo LangGraph actua como supervisor de los asistentes del equipo, coordinando y supervisando sus interacciones para resolver tareas complejas y especializadas. Los asistentes del equipo pueden ser asistentes de tipo LangChain u OpenAI Assistant, y pueden ser añadidos y eliminados del equipo según sea necesario. 

## Como hacer tools que interactuen con Etendo Classic?
Un caso particular de las tools, son las que necesitan interactuar con Etendo Classic. La mejor manera de hacer esto es mediante la API de Event Webhooks de Etendo Classic. Esta API permite autenticarse mediante un token de autenticacion, y accionar un Webhook, el cual puede recibir un diccionario con informacion como parametro y realizar distintas acciones. Dado que este funcionamiento es estandar para Etendo, Copilot provee utilidades para hacerlo de manera mas sencilla. 
Supongamos que queremos desde una tool, accionar un WebHook llamado "UpdateOrderDescription", que actualiza una orden en Etendo Classic, y que recibe un nro de documento y una descripcion.
Para hacer esto, debemos crear una tool que accione el WebHook, y que reciba como parametros el nro de documento y la descripcion. La tool seria la siguiente:

``` python
from typing import Type, Dict

from copilot.core.etendo_utils import call_webhook, get_etendo_token, get_etendo_host
from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper, ToolOutput, ToolOutputMessage


class UpdateSOToolInput(ToolInput):
    documentNo: str = ToolField(description="DocumentNo of the Sales Order")
    description: str = ToolField(description="New description to set in the Sales Order")


class UpdateSOTool(ToolWrapper):
    name = "UpdateSOTool"
    description = "Set description in a Sales Order by DocumentNo2"
    args_schema: Type[ToolInput] = UpdateSOToolInput
    return_direct: bool = False

    def run(self, input_params: Dict = None, *args, **kwargs) -> ToolOutput:
        documentNo = input_params['documentNo']
        description = input_params['description']
        token = get_etendo_token()
        # Build the body of the request
        body = {
            "documentNo": documentNo,
            "description": description
        }
        url = get_etendo_host()
        response = call_webhook(url=url, webhook_name="UpdateOrderDescription", access_token=token, body_params=body)
        return ToolOutputMessage(message=response)
```
Como podemos ver, la tool utiliza las utilidades de provistas por el Core de Copilot. Para el caso de la tool, se utilizan las funciones get_etendo_token y get_etendo_host, que devuelven el token de autenticacion y el host de Etendo Classic respectivamente. Cuando se llama a Copilot, Classic hace de "proxy" manejando la sesiones y brindandole a Copilot un token para poder trabajar en la sesion del usuario, el cual es entregado mediante la funcion get_etendo_token. 
Por otro lado, get_etendo_host devuelve la URL de Etendo Classic, la cual es necesaria para accionar el WebHook. Este host es configurado en el archivo de configuracion del gradle.properties. Finalmente, la funcion call_webhook es la que acciona el WebHook, y recibe como parametros la URL de Etendo Classic, el nombre del WebHook, el token de autenticacion y los parametros que recibe el WebHook. Estas utilidades permiten llamar a WebHooks de Etendo Classic de manera sencilla, dejando solo la logica del armado del cuerpo del request y la logica de la tool en si.
