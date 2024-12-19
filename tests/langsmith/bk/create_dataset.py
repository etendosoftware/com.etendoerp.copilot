from langsmith import Client

questions = [
    "what's the weather in sf",
    "whats the weather in san fran",
    "whats the weather in tangier"
]
answers = [
    "It's 60 degrees and foggy.",
    "It's 60 degrees and foggy.",
    "It's 90 degrees and sunny.",
]

ls_client = Client()
dataset = ls_client.create_dataset(
    "weather agent",
    inputs=[{"question": q} for q in questions],
    outputs=[{"answers": a} for a in answers],
)
