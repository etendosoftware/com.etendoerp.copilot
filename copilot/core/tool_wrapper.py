from transformers import Tool


class ToolWrapper(Tool):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.enabled = True

    def enable(self):
        self.enabled = True

    def disable(self):
        self.enabled = False
