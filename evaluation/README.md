# Instructions

## Environment Preparation
*	Compilation and environment setup.
*	Copilot running in Docker.
*	Ensure the module with the datasets is located in the modules folder.

## Running Examples
*	Navigate to the build/copilot directory, which should contain all the Copilot files and the “collected” tools in the tools folder.
*	Create a virtual environment:
```bash
ENV_NAME=".venv_execution"; [ ! -d "$ENV_NAME" ] && python3 -m venv "$ENV_NAME"; source "$ENV_NAME/bin/activate"
```
*	Install the required dependencies:
```bash
pip install -r requirements.txt
```
*	Run the following command to execute the Copilot agent:
```bash
PYTHONPATH=$(pwd) python3 evaluation/execute.py  --user=admin --password=admin --etendohost=http://localhost:8080/etendo --envfile=../../gradle.properties --dataset=../../modules/com.etendoerp.copilot.agents/dataset --agent_id=49D1735ACAFE48E99A4A5CCFBBE6946C --k=1
```
Parameter explanations:
	*	--dataset: path to the dataset to use. This is relative to the build/copilot folder.
	*	--k: number of repetitions for each example.
	*	--agent_id: ID of the agent whose examples will be executed.
	*	--user: Etendo username.
	*	--password: Etendo password.
	*	--envfile: path to the gradle.properties file containing environment variables. If omitted, default environment variables are used.
	*	--etendohost: Etendo host where the script is running. If omitted, Copilot’s host is used. This is necessary when running outside Docker.
	*	To use an authentication token instead, you can add:
	*	--token=your_token_here

## Saving Examples
* To save examples, you can run the following command:

``` bash
PYTHONPATH=$(pwd) python3 evaluation/execute.py  --user=admin --password=admin --etendohost=http://localhost:8080/etendo --envfile=../../gradle.properties --dataset=../../modules/com.etendoerp.copilot.agents/dataset --agent_id=49D1735ACAFE48E99A4A5CCFBBE6946C --save=20a3a6a8-6b08-4f28-9d71-90fea1ca44d1
```
Parameter explanations:
	*	--user: Etendo username.
	*	--password: Etendo password.
	*	--envfile: path to the gradle.properties file with environment variables. If omitted, default values are used.
	*	--etendohost: Etendo host where the script is running. Required if running outside Docker.
	*	To use an authentication token instead, add:
	*	--token=your_token_here

Parameters for saving the example:
	*	--save: run ID of the example to be saved.
	*	--dataset: path to the dataset to use. This is relative to the build/copilot folder.
	*	--agent_id: ID of the agent to which the example will be saved.
