# Second stage, copy over the requirements and install them
FROM python:3.12.9-slim
RUN apt update && apt install -y libzbar0
# Create folders
RUN mkdir /checkpoints
# Set working directory
WORKDIR /app
RUN mkdir vectordbs
CMD pip install -r /app/requirements.txt && python run.py
