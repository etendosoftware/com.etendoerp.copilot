# Instructions

## Environment Preparation
* Compilation and environment setup.
* Copilot built: In other words, run the gradle task copilot.build. Check that the tools are collected.
* Ensure the module with the datasets is located in the modules folder.
* Sync successfully each assistant to run the tests.

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

# Execute evaluations

*	Run the following command to execute the Copilot agent:
```bash
PYTHONPATH=$(pwd) python3 evaluation/execute.py  --user=admin --password=admin --etendohost=http://localhost:8080/etendo --envfile=../../gradle.properties --dataset=../../modules/com.etendoerp.copilot.agents/dataset --agent_id=49D1735ACAFE48E99A4A5CCFBBE6946C --k=1
```
### Parameter explanations:

| Parameter               | Description                                                                                                                 |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| --dataset               | path to the dataset to use. This is relative to the build/copilot folder.                                                   |
| --k                     | number of repetitions for each example.                                                                                     |
| --agent_id              | ID of the agent whose examples will be executed.                                                                            |
| --user                  | Etendo username.                                                                                                            |
| --password              | Etendo password.                                                                                                            |
| --envfile               | path to the gradle.properties file containing environment variables. If omitted, default environment variables are used.    |
| --etendohost            | Etendo host where the script is running. If omitted, Copilot’s host is used. This is necessary when running outside Docker. |
| --token=your_token_here | To use an authentication token instead, you can add it                                                                      |

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

## Generating the Dataset

This section describes how to generate variants for a dataset for the Copilot agent.

| *Parameter*     | *Description*                                                                                 | *Example*                                                                                        |
|-----------------|-----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| --columns       | The columns to be used for generating the dataset. The columns should be separated by commas. | "Search Key,Name,Category ID,Price List Version,Price"                                           |
| --num_templates | The number of templates to be generated.                                                      | 5                                                                                                |
| --output        | The output file where the generated dataset will be saved.                                    | ../../com.etendoerp.copilot.agents/dataset/767849A7D3B442EB923A46CCDA41223C/prod_templates-3.txt |
| --input         | The input file containing the dataset to be used for generating the variants.                 | ../../com.etendoerp.copilot.agents/dataset/767849A7D3B442EB923A46CCDA41223C/1000-products.csv    |
| --prompt        | The prompt to be used for generating the dataset. The prompt should be a string.              | "Ask to the assistant to create a product with the given data"                                   |

``` bash
PYTHONPATH=$(pwd) python3 gen_variants.py --columns "Search Key,Name,Category ID,Price List Version,Price" --num_templates 5 --output ../../com.etendoerp.copilot.agents/dataset/767849A7D3B442EB923A46CCDA41223C/prod_templates-3.txt ../../com.etendoerp.copilot.agents/dataset/767849A7D3B442EB923A46CCDA41223C/1000-products.csv "Ask to the assistant to create a product with the given data"
```

## Using the generated dataset

To use this inputs you must modify the dataset.json file in the dataset folder.

For example, for the following input:

``` json
[
    {
        "run_id": "390a7874-7dfe-4694-a5be-7855da7b97a8",
        "messages": [
            {
                "role": "user",
                "content": "Create product with this data \"Category ID\" : \"52DBC3E9A82C47F2B6298CBC3E12DA67\"' \"Price\":\"1000\" price list version: FDE536FE9D8C4B068C32CD6C3650B6B8  ' ' seachkey: 228 BARRA HUECA GRANDE PANTUPAS name: 228 BARRA HUECA GRANDE PANTUPAS"
            }
        ],
        "considerations": "In this call, te AI must create a product with the provided data. The creation of the product price is a subsequent step that will be handled in a different call. The AI should not include the product price creation in this call.",
        "expected_response": {
            "role": "assistant",
            "content": "",
            "tool_calls": [
                {
                    "id": "call_FI8tskwHyGiFWaYSKerYxWaA",
                    "function": {
                        "name": "POST_sws_com_etendoerp_etendorx_datasource_Product",
                        "arguments": "{\"body\": {\"searchKey\": \"228 BARRA HUECA GRANDE PANTUPAS\", \"name\": \"228 BARRA HUECA GRANDE PANTUPAS\", \"productCategory\": \"52DBC3E9A82C47F2B6298CBC3E12DA67\", \"description\": \"228 BARRA HUECA GRANDE PANTUPAS\"}}"
                    },
                    "type": "function"
                }
            ]
        },
        "creation_date": "2025-05-14-10:19:10"
    }
]
```

You should modify the dataset.json file to include the generated variants. The modified dataset.json file would look like this:

``` json
[
    {
        "run_id": "390a7874-7dfe-4694-a5be-7855da7b97a8",
        "messages": [
            {
                "id": "10",
                "role": "user",
                "content": "Create product with this data \"Category ID\" : \"52DBC3E9A82C47F2B6298CBC3E12DA67\"' \"Price\":\"1000\" price list version: FDE536FE9D8C4B068C32CD6C3650B6B8  ' ' seachkey: 228 BARRA HUECA GRANDE PANTUPAS name: 228 BARRA HUECA GRANDE PANTUPAS"
            }
        ],
        "considerations": "In this call, te AI must create a product with the provided data. The creation of the product price is a subsequent step that will be handled in a different call. The AI should not include the product price creation in this call.",
        "expected_response": {
            "role": "assistant",
            "content": "",
            "tool_calls": [
                {
                    "id": "call_FI8tskwHyGiFWaYSKerYxWaA",
                    "function": {
                        "name": "POST_sws_com_etendoerp_etendorx_datasource_Product",
                        "arguments": "{\"body\": {\"searchKey\": \"228 BARRA HUECA GRANDE PANTUPAS\", \"name\": \"228 BARRA HUECA GRANDE PANTUPAS\", \"productCategory\": \"52DBC3E9A82C47F2B6298CBC3E12DA67\", \"description\": \"228 BARRA HUECA GRANDE PANTUPAS\"}}"
                    },
                    "type": "function"
                }
            ]
        },
        "creation_date": "2025-05-14-10:19:10",
        "variants": [
            {
                "messages": [
                    {
                        "id": "10",
                        "role": "user",
                        "content": "@{prod-requests.txt}"
                    }
                ]
            }
        ]
    }
]
```

The variants field contains the generated variants for the input adding "id" on the input message to replace it with the
generated. The `@{prod-requests.txt}` placeholder will be replaced with the actual generated variants when the dataset is
executed. Note that the variants field is an array, so you can add multiple variants for each input. 

# Run bulk task eval

* To run the bulk task evaluation, you can use the following command:

``` bash
	python3 ${COPILOT_PATH}/bulk_tasks_eval.py --envfile ../../../../gradle.properties --etendo_url http://localhost:8080/etendo --csv 1000-products.csv --template prod-bulk-templates.txt --table m_product
```

| *Parameter*  | *Description*                                                                                       | *Example*                     |
|--------------|-----------------------------------------------------------------------------------------------------|-------------------------------|
| --envfile    | Path to the gradle.properties file with environment variables. If omitted, default values are used. | ../../../../gradle.properties |
| --etendo_url | Etendo host where the script is running. Required if running outside Docker.                        | http://localhost:8080/etendo  |
| --csv        | Path to the CSV file containing the dataset to be used for generating the variants.                 | 1000-products.csv             |
| --template   | Path to the template file containing the dataset to be used for generating the variants.            | prod-bulk-templates.txt       |
| --table      | Name of the table to be used for generating the variants.                                           | m_product                     |



