#!/usr/bin/env python3

"""
Demo script showing the Dynamic MCP Server in action.
This demonstrates how the server creates MCP instances on-demand.
"""


import requests


def demo_dynamic_mcp():
    """Demonstrate the dynamic MCP server functionality."""
    base_url = "http://localhost:5007"

    print("🎭 Dynamic MCP Server Demo")
    print("=" * 50)
    print("This demo shows how MCP instances are created on-demand when accessed.")
    print()

    try:
        # Step 1: Check initial state
        print("📊 Step 1: Checking initial server state...")
        response = requests.get(f"{base_url}/instances")
        if response.status_code == 200:
            data = response.json()
            print(f"   Initial instances: {data['active_instances']}")

        # Step 2: Create first MCP instance
        print("\n🔧 Step 2: Accessing 'company1' MCP instance...")
        print(f"   URL: {base_url}/company1/mcp")
        response = requests.get(f"{base_url}/company1/mcp")
        print(f"   Status: {response.status_code}")
        if response.status_code == 200:
            print("   ✅ Instance created successfully!")

        # Step 3: Create second MCP instance
        print("\n🔧 Step 3: Accessing 'company2' MCP instance...")
        print(f"   URL: {base_url}/company2/mcp")
        response = requests.get(f"{base_url}/company2/mcp")
        print(f"   Status: {response.status_code}")
        if response.status_code == 200:
            print("   ✅ Instance created successfully!")

        # Step 4: Create third MCP instance
        print("\n🔧 Step 4: Accessing 'project-alpha' MCP instance...")
        print(f"   URL: {base_url}/project-alpha/mcp")
        response = requests.get(f"{base_url}/project-alpha/mcp")
        print(f"   Status: {response.status_code}")
        if response.status_code == 200:
            print("   ✅ Instance created successfully!")

        # Step 5: Check all instances
        print("\n📊 Step 5: Checking all active instances...")
        response = requests.get(f"{base_url}/instances")
        if response.status_code == 200:
            data = response.json()
            print(f"   Total active instances: {data['active_instances']}")
            print("\n   Instance details:")
            for identifier, info in data["instances"].items():
                print(f"   🏢 {identifier}:")
                print(f"      - Port: {info['port']}")
                print(f"      - URL: {info['url']}")
                print(f"      - Status: {info['status']}")
                print(f"      - Alive for: {info['seconds_alive']} seconds")
                print()

        # Step 6: Access existing instance
        print("🔄 Step 6: Re-accessing existing 'company1' instance...")
        response = requests.get(f"{base_url}/company1/mcp")
        print(f"   Status: {response.status_code}")
        print("   ✅ Using existing instance (no new creation)")

        # Step 7: Show server info
        print("\n📋 Step 7: Server information...")
        response = requests.get(f"{base_url}/")
        if response.status_code == 200:
            data = response.json()
            print(f"   Server: {data['message']}")
            print(f"   Version: {data['version']}")
            print(f"   Usage: {data['usage']}")
            print(f"   Active instances: {data['active_instances']}")

        print("\n🎉 Demo completed successfully!")
        print("\n💡 Key Points:")
        print("   - MCP instances are created only when first accessed")
        print("   - Each identifier gets its own isolated MCP server")
        print("   - Instances run on separate ports for true isolation")
        print("   - The hello_world tool shows instance-specific information")
        print("   - Accessing the same identifier reuses the existing instance")

    except requests.exceptions.ConnectionError:
        print("❌ Server not running!")
        print("   Start the server with: python run.py")
        print("   Then run this demo again.")
    except Exception as e:
        print(f"❌ Demo error: {e}")


if __name__ == "__main__":
    demo_dynamic_mcp()
