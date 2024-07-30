# Introduction to Etendo Copilot
## What is an LLM?
LLM (Large Language Model) is a large-scale language model trained on enormous amounts of textual data. It uses machine learning techniques, particularly deep neural networks, to process and generate text in a way that mimics human language. These models are capable of:

1. Understanding the context and meaning of words and phrases.
2. Generating coherent and relevant text in response to given inputs.
3. Performing various tasks such as translation, summarization, text writing, and more.

### Who is OpenAI?

OpenAI is an artificial intelligence research organization founded in December 2015 by Elon Musk, Sam Altman, Greg Brockman, Ilya Sutskever, John Schulman, and Wojciech Zaremba. OpenAI's mission is to ensure that artificial general intelligence (AGI) benefits all of humanity.

### How did OpenAI revolutionize the world with its language models?

OpenAI revolutionized the field of artificial intelligence with the development of advanced language models, among which the following stand out:

1. GPT (Generative Pre-trained Transformer):
   - GPT-2 (2019): A model capable of generating surprisingly coherent and contextualized text, although its capabilities and potential misuse raised significant concerns about AI ethics and safety.
   - GPT-3 (2020): With 175 billion parameters, GPT-3 is one of the largest and most advanced models ever created, capable of performing natural language tasks with high precision and creativity.
   - GPT-4 (2023): The most advanced version that continues to improve in understanding, generation, and specific language tasks.

### Why are LLMs useful for performing many tasks?

LLMs are useful because they can:

1. Automate repetitive and tedious tasks: Such as report writing, summary generation, and automatic responses in customer service.
2. Assist in creativity: Helping writers, artists, and developers generate ideas, texts, and scripts.
3. Improve accessibility: Translating texts in real-time, providing subtitles, and generating accessible content for people with disabilities.
4. Process and analyze data: Facilitating the analysis of large volumes of text, identifying patterns, and extracting relevant information.
5. Personalize experiences: Creating personalized content based on user preferences and behaviors.

These models have transformed various industries, including technology, education, healthcare, and entertainment, by providing advanced tools that enhance efficiency, creativity, and accessibility.

### How did a model oriented to completing text become an assistant that helps users perform tasks?
Initially, the artificial intelligence (AI) developed by OpenAI focused on continuing texts coherently, which was already an impressive achievement. However, as these language models, such as GPT-3 and GPT-4, were refined, their ability to understand and generate text translated into the ability to solve specific tasks. By being trained with massive and varied data, these models acquired competencies in multiple domains, from answering complex questions, writing essays, translating between languages, to performing sentiment analysis and code programming. This evolution allowed AI to transition from being a simple autocomplete tool to becoming a versatile and powerful assistant capable of tackling complex problems and performing a wide range of tasks with precision and efficiency.

## What is Etendo Copilot?

Copilot is a module for Etendo Classic, which provides a platform to create and manage personalized assistants based on advanced LLM models. These assistants can be used to perform specific tasks, answer questions, generate content, and much more. Copilot allows users to leverage the power of artificial intelligence to improve their productivity, creativity, and efficiency in various areas. The Etendo Copilot module allows customizing the behavior of the assistants, feeding them with specific data, and developing additional tools to extend their capabilities, allowing the assistant to perform actions. These functionalities are called "tools" and enable assistants to interact with other systems, perform automated tasks, and provide a more complete and personalized experience to users.

## What does the Etendo Copilot module contain?

### Etendo Copilot Windows in Etendo Classic
- **Assistants**: Displays a list of available assistants, allowing the creation, editing, and management of personalized assistants.
- **Tools**: Allows the development and management of additional tools that extend the capabilities of the assistants, enabling interaction with other systems and performing automated tasks.
- **Assistant Access**: Allows configuring the access and usage permissions of the assistants, defining who can interact with them and what actions they can perform.
- **Knowledge Base File**: Allows adding and managing documents and data that feed the assistants' knowledge base, improving their ability to answer questions and perform tasks.

### Copilot Chat
With the Copilot module, an integrated chat interface in Etendo Classic is included, allowing interaction with the assistants to which the user has access. This interface provides an intuitive and efficient way to communicate with the assistants, make inquiries, and receive real-time responses.

### Copilot API
In addition to the chat interface, Copilot provides an API that allows developers to integrate the assistants into other systems and applications. This API allows interaction with the assistants, sending queries, receiving responses, and performing specific actions through a programmatic interface.

### LangGraph
Due to the specialization of the assistants and the diversity of tasks they can perform, Copilot includes a specialized type of assistant called "LangGraph," which is based on the homonymous library. This type of assistant acts as a "Supervisor" of other assistants, forming work teams (these assistants are called "Team Members") that collaborate to solve complex and specialized tasks. This way, several assistants can perform specific tasks, and the user can interact with the supervisor, who distributes the tasks among the team members.

## Why and when to use Etendo Copilot?
Etendo Copilot is a powerful and versatile tool that can be used in a wide variety of contexts and situations. Some of the reasons to use Etendo Copilot include:

1. Automating repetitive tasks: Copilot can perform tedious and repetitive tasks efficiently and accurately, freeing users from monotonous work and allowing them to focus on more creative and strategic tasks.
2. Assisting in decision-making: Copilot can provide relevant information, data analysis, and recommendations based on advanced models, helping users make informed and accurate decisions.
3. Generating content: Copilot can quickly and effectively write texts, reports, and documents, saving time and effort in content creation.
4. Leveraging generic developments: Copilot can be used to implement generic and reusable solutions in different contexts, improving efficiency and consistency in task execution. By explaining to an assistant how to perform a task, a tool can be leveraged for use in different contexts.
5. Interacting with other systems: Copilot can interact with other systems and applications through customized tools, enabling the integration and automation of complex and heterogeneous processes.

In summary, Etendo Copilot can be very useful in tasks that do not have a direct solution but require reasoning and information processing to reach a solution.

# How Etendo Copilot Works
As mentioned earlier, Etendo Copilot is a module for Etendo Classic, so its installation is the same as any other module.
Once installed, the Copilot windows can be accessed from the Etendo Classic menu, where assistants and tools can be created, edited, and managed. To use Copilot, a service must be started using a Gradle command, using the Etendo Gradle plugin. This service is responsible for communicating Etendo Classic with LLM providers (such as OpenAI) and managing interactions with assistants and tools. This service is containerized with Docker, so Docker must be installed on the machine where the service will be started.
The command to start the Copilot service is as follows:
``` bash
./gradlew copilot.start --info
```
This command will start the Copilot service. It may take a few minutes to start, as it needs to download Docker images and configure the environment.

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

## Copilot Configuration
Copilot requires certain basic configurations, which must be made in the `gradle.properties` file of Etendo. These configurations must also be reflected in the `config/Openbravo.properties` file. These configurations will be used by both Etendo Classic to communicate with Copilot and by Copilot to function and communicate with LLM providers. The Copilot Docker container will use these configurations as environment variables. The basic configurations that need to be made are:

| Property | Description | Default Value | Mandatory |
| --- | --- | --- | --- |
| OPENAI_API_KEY  | OpenAI API key used by Etendo Copilot | sk-proj-********* | Yes |
|COPILOT_HOST|Host used in Etendo Classic to communicate with Copilot | localhost | No |
| COPILOT_PORT  | Port used by the Etendo Copilot service  | 5005  | No |
| AGENT_TYPE  | Default agent type in Etendo Copilot (*)| langchain | No |
| CONFIGURED_TOOLS_FILENAME | Name of the tool configuration file used by Etendo Copilot| tools_config.json | No  |
| DEPENDENCIES_TOOLS_FILENAME | Name of the tool dependencies file used by Etendo Copilot| tools_deps.toml | No |
| OPENAI_MODEL  | OpenAI language model used by Etendo Copilot by default.(*) | gpt-4o  | No |
| SYSTEM_PROMPT | System startup text used by Etendo Copilot. (*) | You are a very powerful assistant that can use a set of tools to do a lot of things. You can use the output of one tool as the input of another tool. So, you can create a chain of tools to solve complex problems | Yes |
| LANGCHAIN_TRACING_V2  | LangChain tracing indicator used by Etendo Copilot  | False  | No |
| LANGCHAIN_ENDPOINT  | LangChain endpoint used by Etendo Copilot | https://api.smith.langchain.com | No |
| LANGCHAIN_API_KEY | LangChain API key used by Etendo Copilot  | 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 | No |
| LANGCHAIN_PROJECT | LangChain project used by Etendo Copilot  | LocalValentin | No |
| COPILOT_DEBUG | Etendo Copilot debug indicator, this indicates to Copilot to print debug information to the console. | False  | No |
| ETENDO_HOST | URL of Etendo Classic used by Etendo Copilot to communicate with Classic (**)  | http://host.docker.internal:8080 | No |

- (*) These values are mandatory from the Assistant configuration in Etendo Classic but can be optional through the Copilot API.
- (**) This value will apply if Docker is used to start Etendo Classic and Copilot on the same machine. If Docker is used to start Etendo Classic and Copilot on different machines, the value of this property must be changed to the IP or Domain of the machine where Etendo Classic is located. It is always recommended to use the domain if it is a server.

Some tools may require additional environment variables, which must be configured in the `gradle.properties` file of Etendo and in the `config/Openbravo.properties` file.

## What is an assistant?
A Copilot assistant is an artificial intelligence agent specialized in performing specific tasks, answering questions, and generating relevant content. Copilot assistants are based on advanced language models, such as GPT-4o, which allow them to understand the context, meaning, and intentions of the user and generate coherent and accurate responses based on the given input. Assistants can be customized and trained to adapt to the user's needs and preferences, improving their ability to perform specific tasks and provide a personalized experience. Since language models have prior knowledge of all kinds (up to the date of their training), assistants can answer a wide variety of questions and perform diverse tasks, from text writing, language translation, data analysis, to code programming. It is most likely that the assistant does not have knowledge of events that occurred after its training date or very specific knowledge that was not included in its training. Additionally, they do not have deterministic behavior, so they may give different answers to the same question on different occasions or make different assumptions based on the same question. This raises the need to specify a prompt to provide the assistant with context about the tasks, what information it can receive, what type of response is expected, and what workflows should be followed, allowing the assistant's behavior to be modeled. Finally, to enable assistants to obtain knowledge beyond their initial training, they can be fed with additional documents and data, which are stored in the assistant's knowledge base. These documents can be added and managed from the "Knowledge Base File" window in Etendo Classic.

## What is a tool?
Initially, LLMs can only return information, either from their training or from the information provided in the prompt. However, in many cases, it is necessary for the assistant to perform actions, such as interacting with other systems, performing automated tasks, or processing data in a specific way. For this, additional tools can be developed to extend the assistants' capabilities, allowing them to perform more complex tasks and actions. These tools are called "tools" and can be developed and managed from the "Tools" window in Etendo Classic. Tools are Python scripts that define a series of actions and tasks that the assistant can perform. These actions can include interacting with external APIs, processing data, generating content, and executing commands on external systems.

### What is the utility of tools?
Tools allow assistants to perform specific and customized tasks, adapting to the user's needs and preferences. Tools can be developed by users or third parties and can be shared and reused in different contexts and situations. Tools can be added to assistants to extend their capabilities and improve their functionality, allowing users to perform more advanced and specialized tasks. The premise of tools is that the assistant can use them strategically to perform tasks, meaning the assistant can use the output of one tool as the input of another tool, allowing the creation of complex and specialized workflows.

### What benefits do tools bring to assistants? Do they have to be complex?
It is better to have small tools and a well-explained prompt than a very large and complex tool. Tools should be as granular and generic as possible so that they can be reused in different contexts and situations.
For example, if we need an assistant to download a file from a URL, summarize it, and then upload it to an external system, we can create three tools, one to download the file, another to summarize it, and another to upload it, and the assistant can use these tools in sequence to perform the task. Then, given this example, if we add another tool that allows the assistant to send an email, we could request that instead of uploading the file to an external system, it sends it by email. This way, with sufficiently "granular" tools, complex and specialized tasks can be performed. Additionally, the assistant can adapt to unforeseen situations.

## How to interact with an assistant?
Copilot assistants can be interacted with in different ways, depending on the user's needs and preferences. Some common ways to interact with an assistant include:

1. Chat interface: Copilot provides an integrated chat interface in Etendo Classic, allowing the user to send queries, receive responses, and perform tasks through a real-time conversation. This interface is intuitive and efficient, allowing the user to communicate with the assistant naturally and fluently. The interface consists of an assistant selector at the top, which allows selecting the assistant to interact with, only showing the assistants the user has access to. At the bottom, there is the chat, where messages can be sent to the assistant and responses received. When making a request to the assistant, an icon indicates whether a tool is being executed or information is being processed. Additionally, if interacting with a LangGraph assistant, an icon indicates which team member assistant is being interacted with.

2. Copilot API: In addition to the chat interface, Copilot provides an API that allows developers to integrate the assistants into other systems and applications. This API allows sending messages and receiving responses from an assistant.

![image](https://PlaceHolder)

# Development with Etendo Copilot
Etendo Copilot provides a platform to develop personalized assistants and additional tools that extend the assistants' capabilities. Development with Copilot involves creating, editing, and managing assistants and tools, as well as feeding data and documents into the assistants' knowledge base. Copilot leverages the modularity of Etendo Classic, allowing developers to customize assistants and tools, enabling these modules to add tools and store, if necessary, the configuration of the assistants.

## Structure of modules in Etendo Copilot
![image](https://PlaceHolder)
- Tools Folder: Contains the tools developed by the user. Each tool is a Python script that implements an interface defined by Copilot. Tools can be developed and managed from the "Tools" window in Etendo Classic.
- Tests Folder: Contains unit tests for the tools. These tests are necessary to ensure the correct functioning of the tools and to detect possible errors or failures in their implementation.
- tool_deps.toml File: Contains the dependencies of the tools. This file is used by Copilot to install the necessary dependencies to execute the tools.
- build.gradle File: Contains the Gradle configuration for the Copilot module. This file is necessary for Copilot to recognize the module.
- Application Dictionary Files: Contain information about the assistants, in case there are exported assistants in the module. They should also contain information about the tools, as they must be registered in the application dictionary for Copilot to recognize them.

!info
  When starting the Copilot service, it scans the classic modules and collects the Python files of the tools. Additionally, it also collects dependency files of the tools to install them within its Docker container.

## How to create an assistant?

From the "Assistant" window, we can create, edit, and delete assistants. We have mandatory and optional data that we must complete to create an assistant. The mandatory data are:
- Assistant Name: Name that identifies the assistant.
- Assistant Description: Brief description of the assistant, what it can do, or what task it can help with.
- Prompt: Text that defines the assistant's context, personality, and behavior. This text is very important as it indicates to the assistant what type of information it can receive, what type of response is expected, and what workflows should be followed. This text models the assistant's behavior.
- Assistant Type: Type of assistant being created. The types of assistants are:
  - LangChain: Assistant based on the LangChain library, which provides its own API for creating assistants.
  - OpenAI Assistant: Assistant based on the OpenAI Assistants API, which uses advanced language models to answer questions and perform specific tasks.
  - LangGraph: Assistant specialized in coordinating and supervising other assistants, forming work teams that collaborate to solve complex and specialized tasks.

| Info |
| ------------- | 
| Generally, **LangChain and OpenAI Assistant** are the **simplest and almost equivalent**, as both use the OpenAI API to answer questions and perform tasks. The main difference is that **LangChain assistants do not upload knowledge base files to OpenAI**, but keep them on their own server. **LangGraph assistants** are more complex as they **coordinate and supervise other assistants**, creating a graph of assistants that collaborate to solve complex and specialized tasks.|

The optional data are:
- LLM Provider: The LLM provider to be used for the assistant. If not specified, the default provider will be used.
- Language Model: The specific language model to be used for the assistant. If not specified, the default model defined based on the LLM provider will be used.
- Temperature: A number between 0 and 2 that indicates how creative or risky the assistant is. A value of 0 indicates that the assistant is very conservative and sticks to what it knows, while a value of 2 indicates that the assistant is very creative and risky. If not specified, the default temperature will be used.

## How to add documents to an assistant's knowledge base?
To add custom documents to an assistant's knowledge base, you must access the "Knowledge Base File" window in Etendo Classic. From this window, you can add, edit, and delete documents that feed the assistant's knowledge base. Documents can be of different types depending on their origin:
- HQL: Documents generated from HQL queries in Etendo Classic. This is executed when requested by the assistant, either during synchronization or execution.
- Attached File: Document attached and uploaded to the assistant. This document is stored as a traditional Classic attachment and synchronized with the assistant.
- Remote File: Document downloaded from a URL. This document is downloaded when requested by the assistant, either during synchronization or execution.

Once the documents are added in their window, they must be linked to the assistant from the "Assistants" window. To do this, select the assistant to which you want to add the document, and in the "Knowledge Base" tab, select the document you want to add. It is necessary to specify the type of attachment, in other words, how the document is integrated into the assistant's knowledge base:
- Add to Knowledge Base: Adds the document to the assistant's knowledge base, allowing the assistant to use it to answer questions and perform tasks.
- Append to System Prompt: Adds the document's content to the assistant's prompt, allowing the assistant to use it to determine its behavior and personality. This is done when synchronizing the assistant (OpenAI Assistant) or when making the first query to the assistant (LangChain).
- Append to Question: Adds the document's content to the question asked to the assistant, useful when context is required at the time of the query. An example of this is the current date and time or the weather at the time of the query.

| Info |
| ------------- |
| It is important to always synchronize the assistant after adding documents to the knowledge base so that the assistant can use them in its responses.|

## How to configure/develop a tool for the assistant?

### How to add a tool to the assistant?
To add a tool to the assistant, use the "Skill/Tool" tab, where you can link the tools you want to add to the assistant. The "Copilot Extensions" modules provide several modules that include general-purpose tools that can be used in different contexts.

### How to develop a tool for the assistant?
To develop a tool for the assistant, you must create a module in Etendo Classic that contains the tool (if necessary).
Tools are programmatic Python functions that follow a specific format. It is recommended to create unit tests for the tools to ensure their correct functioning and detect possible errors or failures in their implementation and execution.
Let's make an example of a tool called MeanAndStdDev, which calculates the mean and standard deviation of a list of numbers. Suppose we already have the following Python function that calculates the mean and standard deviation of a list of numbers:
``` python
import numpy as np

def calculate_mean_and_std(numbers1, numbers2):
    """
    This function takes two lists of numbers, converts them into numpy arrays,
    and then calculates the mean and standard deviation of the combined numbers.

    Parameters:
    numbers1 (list): The first list of numbers.
    numbers2 (list): The second list of numbers.

    Returns:
    tuple: A tuple with the mean and standard deviation of the combined numbers.
    """
    array1 = np.array(numbers1)
    array2 = np.array(numbers2)
    combined_array = np.concatenate((array1, array2))
    mean = np.mean(combined_array)
    std_dev = np.std(combined_array)
    return mean, std_dev
```
To create the tool in Etendo Classic, you must follow the following format:
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
As we can see, the tool consists of two classes: one that defines the tool's input parameters and one that defines the tool itself. The class that defines the tool's input parameters must inherit from the ToolInput class and define the tool's input parameters. 
The class that defines the tool itself must inherit from the ToolWrapper class and define the tool's parameters, the tool's logic, and the tool's output message. In this case, the tool receives two input parameters, param1 and param2, and returns a message with the result of the operation performed with these parameters.

The result of applying the tool format to the Python function that calculates the mean and standard deviation of a list of numbers would be as follows:

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
A crucial detail is that in this case, the tool uses the numpy library, so it is necessary to import it in the tool. This type of library, being external and not included within the base Copilot, must be imported within the tool's run method, i.e., at the time of executing the tool. To ensure Copilot installs the necessary dependencies for executing the tools, the dependencies must be specified in the tool_deps.toml file. In this case, the MeanAndStdTool tool needs the numpy library, so the tool_deps.toml file would be as follows:

``` toml
[MeanAndStdTool]
numpy = "1.21.2" # Version of numpy needed, or it can be *
```
If we restart Copilot, we should see in its log that it installs the numpy library and loads the MeanAndStdTool tool.

### How to register a tool in the application dictionary?
Once we create the tool file, we must register it in the Etendo Classic application dictionary. This is necessary for Copilot to recognize the tool and to configure it in the assistants for them to use it.
We must go to the "Skill/Tool" window in Etendo Classic as "System Administrator."
Once there, we must create a new record, with the search key being the tool's file name and the "Name" being the tool's full name. Finally, we must execute the "Sync Tool" button to read the tool and store its structure and description in Etendo. This process allows obtaining/updating the tool's metadata, which is used by the assistant to "understand" it. The module to which the tool belongs must be indicated to export these records.

Finally, we can configure the tool in the assistants that need it. It is important to synchronize the assistant after adding the tool so that the assistant can use it in its responses.

| Info |
| ------------- |
| It is very important that the tool's file name, the tool's class name, the name specified in the tool_deps.toml file, and the search key of the record in the application dictionary are the same, as Copilot uses these names to recognize the tool, as it is its unique identifier.|

## How to configure a LangGraph assistant?
To configure a LangGraph assistant, follow the same steps as for configuring a normal assistant, with the difference that you must select the "LangGraph" assistant type in the "Assistants" window. 
Additionally, you must add the assistants that will be part of the LangGraph assistant's work team in the "Team Members" tab of the "Assistants" window. The LangGraph assistant acts as a supervisor of the team members, coordinating and supervising their interactions to solve complex and specialized tasks. The team members can be LangChain or OpenAI Assistant type assistants and can be added and removed from the team as needed.

## How to create tools that interact with Etendo Classic?
A particular case of tools is those that need to interact with Etendo Classic. The best way to do this is through the Etendo Classic Event Webhooks API. This API allows authentication through an authentication token and triggers a Webhook, which can receive a dictionary with information as a parameter and perform various actions. Since this functionality is standard for Etendo, Copilot provides utilities to do it more easily. 
Suppose we want to trigger a WebHook called "UpdateOrderDescription" from a tool, which updates an order in Etendo Classic and receives a document number and a description.
To do this, we must create a tool that triggers the WebHook and receives the document number and description as parameters. The tool would be as follows:

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
As we can see, the tool uses the utilities provided by the Copilot Core. In the case of the tool, the get_etendo_token and get_etendo_host functions are used, which return the authentication token and the Etendo Classic host, respectively. When calling Copilot, Classic acts as a "proxy," managing the sessions and providing Copilot with a token to work in the user's session, which is delivered through the get_etendo_token function. 
On the other hand, get_etendo_host returns the URL of Etendo Classic, which is necessary to trigger the WebHook. This host is configured in the gradle.properties configuration file. Finally, the call_webhook function triggers the WebHook and receives the Etendo Classic URL, the WebHook name, the authentication token, and the parameters received by the WebHook. These utilities allow calling Etendo Classic WebHooks easily, leaving only the logic of building the request body and the tool's logic itself.