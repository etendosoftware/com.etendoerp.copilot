"""Invoice extraction schema for SimpleOcrTool structured output.

Targets OpenAI Responses API strict-schema mode, which requires:
  - `additionalProperties: false` on every object → `extra='forbid'`
  - every field listed in `required` (null is allowed via `Optional`, but
    the key itself must always be present) → no default values
"""

from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class InvoiceLineItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    description: Optional[str] = Field(
        description="Product or service description as it appears on the invoice line.",
    )
    quantity: Optional[float] = Field(
        description="Quantity invoiced for this line.",
    )
    unit_price: Optional[float] = Field(
        description="Net unit price for this line (before tax).",
    )


class InvoiceSchema(BaseModel):
    """Strict invoice extraction payload consumed by useOcrHeaderPrefill.

    Field names match the event payload expected by the purchase-invoice
    prefill hook — do not rename without updating the consumer.
    """

    model_config = ConfigDict(extra="forbid")

    document_no: Optional[str] = Field(
        description="Invoice / document number printed on the document.",
    )
    invoice_date: Optional[str] = Field(
        description="Invoice date in ISO format (YYYY-MM-DD) when possible; "
                    "DD-MM-YYYY or DD/MM/YYYY are also accepted.",
    )
    vendor_name: Optional[str] = Field(
        description="Legal or trade name of the vendor / supplier issuing the invoice.",
    )
    line_items: List[InvoiceLineItem] = Field(
        description="Invoice line items, in the order they appear on the document. "
                    "Empty array if the document has no itemized lines.",
    )
