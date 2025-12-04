"""
Test Document Schema

This schema is used for testing purposes and demonstrates various Pydantic features.
It includes different field types, nested models, optional fields, and validation.
"""

from datetime import date, datetime
from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class DocumentStatus(str, Enum):
    """Status enumeration for documents."""

    DRAFT = "draft"
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"
    ARCHIVED = "archived"


class Priority(str, Enum):
    """Priority levels."""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class Attachment(BaseModel):
    """Represents a file attachment."""

    filename: str = Field(description="Name of the attached file")
    file_size: int = Field(description="Size of the file in bytes", gt=0)
    mime_type: str = Field(description="MIME type of the file")
    url: Optional[str] = Field(default=None, description="URL to download the file")
    checksum: Optional[str] = Field(default=None, description="MD5 or SHA256 checksum")


class ContactInfo(BaseModel):
    """Contact information."""

    name: str = Field(description="Full name of the contact person")
    email: str = Field(description="Email address")
    phone: Optional[str] = Field(default=None, description="Phone number")
    department: Optional[str] = Field(default=None, description="Department name")

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: str) -> str:
        """Validate email format."""
        if "@" not in v:
            raise ValueError("Invalid email format")
        return v.lower()


class Address(BaseModel):
    """Physical address information."""

    street: str = Field(description="Street address")
    street2: Optional[str] = Field(default=None, description="Additional address line")
    city: str = Field(description="City name")
    state: Optional[str] = Field(default=None, description="State or province")
    postal_code: str = Field(description="Postal or ZIP code")
    country: str = Field(description="Country name or ISO code")
    is_primary: bool = Field(default=True, description="Whether this is the primary address")


class AmountDetail(BaseModel):
    """Detailed amount breakdown."""

    subtotal: float = Field(description="Subtotal amount before taxes", ge=0)
    tax_amount: float = Field(default=0.0, description="Tax amount", ge=0)
    discount_amount: float = Field(default=0.0, description="Discount amount", ge=0)
    total: float = Field(description="Total amount after taxes and discounts", ge=0)
    currency: str = Field(default="USD", description="Currency code (ISO 4217)")

    @model_validator(mode="after")
    def validate_total(self):
        """Validate that total equals subtotal + tax - discount."""
        calculated_total = self.subtotal + self.tax_amount - self.discount_amount
        if abs(self.total - calculated_total) > 0.01:  # Allow small rounding differences
            raise ValueError(f"Total {self.total} does not match calculated total {calculated_total}")
        return self


class LineItem(BaseModel):
    """Individual line item in the document."""

    line_number: int = Field(description="Line number", gt=0)
    item_code: str = Field(description="Product or service code")
    description: str = Field(description="Item description")
    quantity: float = Field(description="Quantity ordered", gt=0)
    unit_price: float = Field(description="Price per unit", ge=0)
    tax_rate: float = Field(default=0.0, description="Tax rate percentage (0-100)", ge=0, le=100)
    discount_percent: float = Field(default=0.0, description="Discount percentage", ge=0, le=100)
    line_total: float = Field(description="Total for this line", ge=0)
    notes: Optional[str] = Field(default=None, description="Additional notes for this line")


class ApprovalInfo(BaseModel):
    """Approval information."""

    approved_by: str = Field(description="Name of the approver")
    approved_date: datetime = Field(description="Date and time of approval")
    comments: Optional[str] = Field(default=None, description="Approval comments")
    level: int = Field(default=1, description="Approval level", ge=1, le=5)


class TestdocumentSchema(BaseModel):
    """
    Comprehensive test document schema with various field types.

    This schema demonstrates:
    - Required and optional fields
    - Nested models
    - Lists and unions
    - Enumerations
    - Field validators
    - Model validators
    - Different data types (str, int, float, bool, date, datetime)
    - Constraints (gt, ge, le, etc.)
    """

    # Basic identification fields
    document_id: str = Field(description="Unique document identifier (REQUIRED)")
    document_number: str = Field(description="Human-readable document number (REQUIRED)")
    title: str = Field(description="Document title (REQUIRED)")
    description: Optional[str] = Field(default=None, description="Detailed description")

    # Status and priority
    status: DocumentStatus = Field(default=DocumentStatus.DRAFT, description="Current document status")
    priority: Priority = Field(default=Priority.MEDIUM, description="Document priority level")

    # Dates
    creation_date: date = Field(description="Date when document was created")
    due_date: Optional[date] = Field(default=None, description="Due date if applicable")
    modified_date: Optional[datetime] = Field(default=None, description="Last modification timestamp")

    # Parties involved
    owner: ContactInfo = Field(description="Document owner (REQUIRED)")
    assignee: Optional[ContactInfo] = Field(default=None, description="Person assigned to this document")
    contacts: List[ContactInfo] = Field(
        default_factory=list, description="Additional contacts related to this document"
    )

    # Address information
    billing_address: Optional[Address] = Field(default=None, description="Billing address")
    shipping_address: Optional[Address] = Field(default=None, description="Shipping address")

    # Financial information
    amounts: Optional[AmountDetail] = Field(default=None, description="Amount breakdown")

    # Line items
    line_items: List[LineItem] = Field(default_factory=list, description="Document line items")

    # Attachments
    attachments: List[Attachment] = Field(default_factory=list, description="File attachments")

    # Approval information
    approvals: List[ApprovalInfo] = Field(
        default_factory=list, description="List of approvals (multi-level approval)"
    )

    # Flags and metadata
    is_active: bool = Field(default=True, description="Whether the document is active")
    is_confidential: bool = Field(default=False, description="Confidentiality flag")
    version: int = Field(default=1, description="Document version number", ge=1)

    # Tags and categories
    tags: List[str] = Field(default_factory=list, description="Tags for categorization")
    category: Optional[str] = Field(default=None, description="Document category")

    # Additional notes
    notes: Optional[str] = Field(default=None, description="General notes")
    metadata: Optional[dict] = Field(default=None, description="Additional metadata as key-value pairs")

    @field_validator("document_number")
    @classmethod
    def validate_document_number(cls, v: str) -> str:
        """Ensure document number is not empty."""
        if not v or not v.strip():
            raise ValueError("Document number cannot be empty")
        return v.strip().upper()

    @field_validator("version")
    @classmethod
    def validate_version(cls, v: int) -> int:
        """Ensure version is positive."""
        if v < 1:
            raise ValueError("Version must be at least 1")
        return v

    @model_validator(mode="after")
    def validate_dates(self):
        """Ensure due date is after creation date if both are provided."""
        if self.due_date and self.creation_date:
            if self.due_date < self.creation_date:
                raise ValueError("Due date cannot be before creation date")
        return self

    @model_validator(mode="after")
    def validate_line_items_total(self):
        """Validate that amounts match line items if both are provided."""
        if self.amounts and self.line_items:
            calculated_subtotal = sum(item.line_total for item in self.line_items)
            if abs(self.amounts.subtotal - calculated_subtotal) > 0.01:
                raise ValueError(
                    f"Amounts subtotal {self.amounts.subtotal} does not match "
                    f"line items total {calculated_subtotal}"
                )
        return self
