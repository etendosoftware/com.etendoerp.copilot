# Copilot

Copilot helps users answer questions and assignments through different tools. So far, copilot supports two agents types: `Langchain Agent` and `OpenAI Assistant Agent`.

# How to use Copilot as user
* Make sure [docker](https://docs.docker.com/get-docker/) is installed
* Get the `etendo/etendo_copilot_core` image from [dockerhub](https://hub.docker.com/repository/docker/etendo/etendo_copilot_core/tags?page=1&ordering=last_updated): `docker pull etendo/etendo_copilot_core:develop`
* Once image is downloaded:
    * Set your local configuration copying `.env.sample` into `.env` and set the right values
    * Run a container as: ` docker run -it --env-file .env -p <host_machine_port>:<inside_docker_port> -v $(pwd)/:/app/ etendo/etendo_copilot_core:develop`
    * Make a request sample: `curl -i -X POST -H "Content-Type: application/json" -d '{"question": "What is etendo?"}' http://localhost:<host_machine_port>/question`


# Deploy docker image
This is done automatically from CI for develop and experimental branches.

```
docker build -t etendo/chatbot_etendo .
docker push etendo/chatbot_etendo
```

# FRONT
```
kubectl port-forward -n chat-etendo svc/das 8092:8092
kubectl port-forward -n chat-etendo svc/etendo-retrieval 8085:8080
```

# Backend Development

## Virtual environment

As tool for managing multiple Python versions you can use [pyenv](https://github.com/pyenv/pyenv). `pyenv` does not manage package dependencies, so for this purpose you can use [Poetry](https://python-poetry.org/). It will create an isolated virtual environment for the `etendo-copilot-core` project.

* Install `pyenv`: https://github.com/pyenv/pyenv#installation
* Install `Poetry`: `curl -sSL https://install.python-poetry.org | python3 -`
* Create `etendo-copilot-core` venv using poetry (recommended approach):

```
pyenv install 3.10
pyenv local 3.10
poetry env use 3.10
poetry install
```

Alternative, you can use Docker.

### Torch and MacOS issue
If you are getting this issue from `poetry install`: `Unable to find installation candidates for torch (2.0.1+cpu)`.

Workaround:
```
poetry shell
pip install torch==2.0.1
deactivate
```

Verify installation:
```
poetry run python
>>> import torch
>>> torch.__version__
'2.0.1'
```

### Add new dependencies
* For prod dependency run: `poetry add <dep_name>`
* For dev dependency run: `poetry add <dep_name> --group dev`

## How to run copilot
* Locally outside docker:
	- Copy `.env.sample` into `.env` and set the right values
	- `poetry run python run.py`

* Using docker, make sure `.env` is created and all the variables are set, only then run `docker run --env-file .env -p 5001:5001 etendo/chatbot_etendo`. You can set the port that you want, just be sure to set the same port in the image from `.env` if not, the api will never be reached.

* The `AGENT_TYPE` environment variable should be used to set the agent type. There are two available agent: `langchain` and `openai-assistant`. By default copilot will be executed for `langchain`.

* Mount code as volume: `docker run --env-file .env -p 5001:5001 -v $(pwd)/copilot:/app/copilot etendo/chatbot_etendo`.

## How to run unit test
`poetry run pytest tests`

## OpenAPI URL
You can get the open api (swagger) documentation from `http://localhost:<port>/docs` or `http://localhost:<port>/redoc`

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

4- Restart the copilot container loading the project root folder through a volume: `docker run --env-file .env -p 5001:5001 -v $(pwd):/app etendo/chatbot_etendo`


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

6- Restart the copilot container loading the project root folder through a volume: `docker run --env-file .env -p 5001:5001 -v $(pwd)/tools:/app/tools -v $(pwd)/tools_config.json:/app/tools_config.json etendo/chatbot_etendo`

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
