import os

def read_properties(file_path):
    properties = {}
    with open(file_path, 'r') as file:
        for line in file:
            if '=' in line and not line.startswith('#'):
                key, value = line.strip().split('=', 1)
                properties[key] = value
    return properties


current_dir = os.path.dirname(os.path.abspath(__file__))
gradle_properties_path = os.path.abspath(os.path.join(current_dir, '../../../gradle.properties'))


print(f"gradle.properties path: {gradle_properties_path}")

props = read_properties(gradle_properties_path)

db_config = {
    "database": props.get("bbdd.sid"),
    "user": props.get("bbdd.user"),
    "password": props.get("bbdd.password"),
    "host": "localhost",
    "port": props.get("bbdd.port")
}

print(db_config)
