import os

from connection import model, prompt
from langchain.chat_models import init_chat_model

from copilot.core.utils import get_proxy_url
from dotenv import load_dotenv

load_dotenv()

os.environ["LANGCHAIN_API_KEY"] = str(os.getenv("LANGCHAIN_API_KEY"))
os.environ["LANGCHAIN_ENDPOINT"] = str(os.getenv("LANGCHAIN_ENDPOINT"))
os.environ["OPENAI_API_KEY"] = str(os.getenv("OPENAI_API_KEY"))
os.environ["LANGCHAIN_TRACING_v2"] = "true"   
os.environ["LANGCHAIN_PROJECT"] = "TestAssistantRO"


questions = [
  ("What requirements does Etendo Classic have to be installed?", """To install Etendo Classic, various requirements must be met on both the client and server sides. Here I detail them concisely but completely:

    Client:

        Compatible web browsers:
            Google Chrome: Minimum required version 95, recommended version 97 or higher.
            Apple Safari: Minimum required version 12, recommended version 14 or higher.
            Mozilla Firefox ESR: Minimum required version 78, recommended version 90 or higher.
            Microsoft Edge (Chromium-based): Minimum required version 95, recommended version 97 or higher.

        Network connectivity:
            For up to 10 concurrent users, a download bandwidth of at least 3Mbit/s is required.
            For up to 20 concurrent users, a download bandwidth of at least 10Mbit/s is required.
            For up to 100 concurrent users, a download bandwidth of at least 100Mbit/s is required.

    Server:

        Java-based = multiplatform:
        Etendo runs on any operating system where the Java JDK works, including Windows, various Linux distributions, FreeBSD, Mac OSX, Solaris, and more. It is also compatible with architectures x86, x86_64, IA64, Sparc, PowerPC, and AIX.

        Database:
            Ensure that PostgreSQL is compatible with your target system.

        Software stack:
            Java SE: Supported versions 11, recommended version the latest of 11.x. An installation guide is provided.
            PostgreSQL: Supported versions 10.x, 11.x, 12.x, 13.x, Amazon RDS, with the latest 14.x recommended. An installation guide is provided.
            Apache Tomcat: Supports versions 8.5.x (x ≥ 24), 9.0.x, recommending the latest of 9.0.x. An installation guide is provided.
            Oracle: Version 19c (LTS) is supported and recommended.

    These are the essential requirements for installing and running Etendo Classic, ensuring compatibility of software and hardware, as well as an adequate network connection for the number of users anticipated."""),
  ("How do I install Etendo Classic?", """ To install Etendo Classic, you can follow one of two methods: JAR format installation or source format installation. Both methods are described concisely but completely below:

    JAR Format Installation:

    1. Clone the Etendo base project into a temporary directory:
    ```bash
    cd /tmp
    git clone https://github.com/etendosoftware/etendo_base.git EtendoERP
    2. Copy the sources into the /opt/EtendoERP folder.
    3. Modify the gradle.properties file with your GitHub credentials, following a specific guide to create the credentials.
    4. Change the build.gradle file, removing the core version section and uncommenting the core dependency in the dependencies section.
    5. Modify the gradle.properties file with your environment variables, if necessary.
    6. Run dependencies with ./gradlew dependencies.
    7. Configure the environment with ./gradlew setup.
    8. Install with ./gradlew install smartbuild.
    9. Start Tomcat (on Linux, for example, sudo /etc/init.d/tomcat start).
    10. Open your browser at https://<Public server IP>/<Context Name>.

    Source Format Installation:

    1. Clone the Etendo base project into a temporary directory.
    2. Copy the sources into the /opt/EtendoERP folder.
    3. Modify the gradle.properties file with your GitHub credentials.
    4. Modify the build.gradle file to set the desired core version if you need to change it.
    5. Expand the Etendo base with ./gradlew expand.
    6. Modify the gradle.properties file with your environment variables, if necessary.
    7. Configure the environment with ./gradlew setup.
    8. Install by creating the database, compiling the sources, and deploying to Apache Tomcat with ./gradlew install smartbuild.
    9. Ensure you have the appropriate PostgreSQL configuration in your postgresql.conf.
    10. Start Tomcat (on Linux, for example, sudo /etc/init.d/tomcat start).
    11. Open your browser at https://<Public server IP>/<Context Name>.

    Both methods require prior PostgreSQL configuration and, depending on your choice, modifying the gradle.properties and build.gradle files with the specifications of your environment and preferences."""),
  ("What types of user messages are there in the Etendo Classic interface?", """In the Etendo Classic interface, there are four types of user messages designed to inform or warn the user about different situations that require attention:

    Info: Used to communicate interesting but non-essential information. These messages can inform the user about a change or situation that does not directly affect the execution of processes.

    Success: This message appears after the successful execution of a process. It indicates that the action requested by the user has been completed correctly without errors.

    Error: Used for exceptions and errors, such as when a process was not successfully executed. These messages indicate problems that need to be corrected to continue.

    Warning: Used to inform the user about a system state or event that could cause a problem. These messages are usually preventive, warning the user before taking an action that could be problematic.

Each type of message has a specific purpose and helps users navigate and interact with the application more effectively, providing immediate feedback on the actions taken or problems encountered."""),
  ("How should I configure access to dynamic applications according to the role?", """To configure access to dynamic applications by role in Etendo, follow these steps:

    1.Verify the module installation: Ensure that the Financial Extensions Bundle is installed in Etendo. This is necessary to include dynamic application functionality.

    2.Access the Roles configuration: Go to the Roles configuration window in Etendo.

    3.Select or create a Role: Choose the role you want to give access to the dynamic applications, or create a new one if necessary.

    4.Configure access in the Dynamic App tab: Within the role's configuration, look for the Dynamic App tab. Here you can configure which dynamic applications will be available to users assigned to this role.
        App: Select the application you want to assign to the role from the dropdown menu.
        Version: Assign the specific version of the application you wish to use.
        Active: Check this option if you want the application to be active and accessible for the role.

    5.Save the changes: Once the dynamic applications and their versions for the role are configured, make sure to save the changes.

    By following these steps, you'll be able to configure access to dynamic applications by role in Etendo, allowing for detailed customization of the work environment for different user groups within your organization."""),
    ("Can I install Etendo Classic on an AWS server?", """The following information has been extracted from the article https://docs.etendo.software/latest/getting-started/requirements
    Yes, you can install Etendo Classic on an AWS server. The requirements for running Etendo specify that it operates on a server with a Java-based, multiplatform setup. Since Etendo runs on any platform where the Java JDK works, this includes operating systems like Windows, various Linux distributions, FreeBSD, Mac OSX, and more. Specifically, architectures such as x86, x86_64, IA64, Sparc, PowerPC, AIX are supported, which means AWS servers, which typically run on these architectures, are suitable for hosting Etendo Classic. Additionally, make sure that PostgreSQL is also supported on your chosen AWS instance since it is required for Etendo.""")
]



from langsmith import Client

client = Client()

import uuid

dataset_name = f"Retrieval QA Questions {str(uuid.uuid4())}"
dataset = client.create_dataset(dataset_name=dataset_name)
for q, a in questions:
    client.create_example(
        inputs={"question": q}, outputs={"answer": a}, dataset_id=dataset.id
    )

from langchain_community.document_loaders.recursive_url_loader import RecursiveUrlLoader
from langchain_community.document_transformers import Html2TextTransformer
from langchain_community.vectorstores.utils import filter_complex_metadata
from langchain.text_splitter import TokenTextSplitter
from langchain_openai import OpenAIEmbeddings
from langchain_chroma import Chroma

api_loader = RecursiveUrlLoader("https://raw.githubusercontent.com/etendosoftware/docs/main/compiled_docs.md")
text_splitter = TokenTextSplitter(
    model_name=model,
    chunk_size=2000,
    chunk_overlap=200,
)
doc_transformer = Html2TextTransformer()
raw_documents = api_loader.load()
transformed = doc_transformer.transform_documents(raw_documents)
documents = text_splitter.split_documents(transformed)

embeddings = OpenAIEmbeddings()
filtered_documents = filter_complex_metadata(documents)
vectorstore = Chroma.from_documents(filtered_documents, embeddings)
retriever = vectorstore.as_retriever(search_kwargs={"k": 4})

from langchain.prompts import ChatPromptTemplate
from langchain_openai import ChatOpenAI
from langchain.schema.output_parser import StrOutputParser

from datetime import datetime

Prompt = ChatPromptTemplate.from_messages(
    [
        (
        prompt
        ),
        ("system", "{context}"),
        ("human", "{question}"),
    ]
).partial(time=str(datetime.now()))

model = init_chat_model(model=model, temperature=0, base_url=get_proxy_url())
response_generator = Prompt | model | StrOutputParser()

# The full chain looks like the followinP
from operator import itemgetter

chain = (
    # The runnable map here routes the original inputs to a context and a question dictionary to pass to the response generator
    {
        "context": itemgetter("question")
        | retriever
        | (lambda docs: "\n".join([doc.page_content for doc in docs])),
        "question": itemgetter("question"),
    }
    | response_generator
)


for tok in chain.stream({"question": "What requirements does Etendo Classic have to be installed?"}):
    print(tok, end="", flush=True)


from langchain.smith import RunEvalConfig
import asyncio

eval_config = RunEvalConfig(
    evaluators=["qa", "context_qa", "cot_qa", "embedding_distance"],
    # If you want to configure the eval LLM:
    # eval_llm=ChatAnthropic(model="claude-2", temperature=0)
)

async def run_evaluation():
    _ = await client.arun_on_dataset(
        dataset_name=dataset_name,
        llm_or_chain_factory=lambda: chain,
        evaluation=eval_config,
    )

asyncio.run(run_evaluation())


