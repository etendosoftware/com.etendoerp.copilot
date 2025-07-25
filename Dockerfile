# Second stage, copy over the requirements and install them
FROM python:3.12.9-slim
RUN apt update && apt install -y libzbar0 curl && \
    curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - && \
    apt install -y nodejs && \
    npm install -g npm@latest
WORKDIR /app
CMD pip install -r /app/requirements.txt && python run.py
