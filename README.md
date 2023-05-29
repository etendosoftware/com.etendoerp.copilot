# Setup
Set the environment variable OPENAI_API_KEY with a valid value

# Deploy docker image

docker build -t etendo/chatbot_etendo .
docker push etendo/chatbot_etendo

# Run the command in front

# FRONT
kubectl port-forward -n chat-etendo svc/das 8092:8092
kubectl port-forward -n chat-etendo svc/etendo-retrieval 8085:8080

# BACK
source .env
python3 server.py
