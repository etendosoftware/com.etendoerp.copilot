"""
Invoice Schema

This schema defines the structure for invoice data.
Can be used for:
- OCR extraction structured output
- Tool input validation
- Data transfer between tools
"""

from typing import List, Optional

from pydantic import BaseModel, Field


class InvoiceLine(BaseModel):
    """Represents a single line item in an invoice."""

    product: Optional[str] = Field(default=None, description="Product or service name/description")
    quantity: Optional[float] = Field(default=None, description="Quantity of items")
    unit_price: Optional[float] = Field(default=None, description="Price per unit")
    tax_rate: Optional[float] = Field(
        default=None, description="Tax rate percentage (e.g., 21 for 21% VAT/IVA)"
    )
    total: Optional[float] = Field(
        default=None, description="Total amount for this line (quantity × unit_price)"
    )


class InvoiceAddress(BaseModel):
    """Represents address information for an invoice."""

    street: Optional[str] = Field(default=None, description="Street address")
    city: Optional[str] = Field(default=None, description="City")
    postal_code: Optional[str] = Field(default=None, description="Postal/ZIP code")
    state: Optional[str] = Field(default=None, description="State or province")
    country: Optional[str] = Field(default=None, description="Country name or code")


class InvoiceSchema(BaseModel):
    """
    Structured schema for invoice data.

    This schema is used by OCRAdvancedTool for structured extraction,
    SalesInvoiceCreationTool for invoice creation, and other tools for invoice data handling.

    Required fields for SalesInvoiceCreationTool:
    - cif: Tax ID is mandatory to create/find Business Partner
    - business_partner: Name of the Business Partner
    - date: Invoice date
    - lines: At least one invoice line

    Optional fields:
    - search_key: Invoice reference number
    - address: Address information (will be created as billing address)
    """

    businesspartner: str = Field(
        description="Business Partner name (REQUIRED for invoice creation)",
    )
    cif: str = Field(
        description="Tax identification number - CIF/NIF/VAT (REQUIRED for creating new BP)",
    )
    documentno: str = Field(
        description="Invoice number, reference, or unique identifier (REQUIRED)",
    )
    date: str = Field(description="Invoice date in ISO format YYYY-MM-DD (REQUIRED for invoice creation)")
    address: InvoiceAddress = Field(
        description="Address information for the business partner (REQUIRED, will be created as billing address)"
    )
    lines: List[InvoiceLine] = Field(default_factory=list, description="List of invoice line items")
