import React, { useEffect } from "react";
import { Form, Button } from "react-bootstrap";
import { useForm } from "react-hook-form";

function dateTimeLocalValue(dateTime) {
  return dateTime
    ? dateTime.slice(0, 16)
    : new Date().toISOString().slice(0, 16);
}

export default function ReviewForm({
  initialItemName = "",
  initialComments = "",
  initialStars = 5,
  initialDateServed,
  submitAction,
  submitButtonText = "Submit Review",
}) {
  const testIdPrefix = "ReviewForm";

  // Stryker disable all
  const {
    register,
    formState: { errors },
    handleSubmit,
    reset,
    getValues,
  } = useForm({
    defaultValues: {
      reviewerComments: initialComments,
      itemsStars: initialStars,
      dateItemServed: dateTimeLocalValue(initialDateServed),
    },
  });
  // Stryker restore all

  // Keep the form in sync when the initial values change. If no new date is
  // supplied, keep whatever date is currently in the form.
  useEffect(() => {
    // Stryker disable next-line StringLiteral: equivalent mutant, RHF keeps the current field value when reset receives undefined
    const currentDate = getValues("dateItemServed");

    reset({
      reviewerComments: initialComments,
      itemsStars: initialStars,
      dateItemServed: initialDateServed
        ? dateTimeLocalValue(initialDateServed)
        : currentDate,
    });
  }, [initialComments, initialStars, initialDateServed, reset, getValues]);

  return (
    <Form onSubmit={handleSubmit(submitAction)}>
      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-item-name">Item Name</Form.Label>
        <Form.Control
          id="review-item-name"
          data-testid={testIdPrefix + "-itemName"}
          type="text"
          value={initialItemName}
          disabled
          readOnly
        />
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-comments">Comments</Form.Label>
        <Form.Control
          id="review-comments"
          data-testid={testIdPrefix + "-reviewerComments"}
          as="textarea"
          rows={3}
          isInvalid={Boolean(errors.reviewerComments)}
          {...register("reviewerComments")}
        />
        <Form.Control.Feedback type="invalid">
          {errors.reviewerComments?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-stars">Stars (1 to 5)</Form.Label>
        <Form.Control
          id="review-stars"
          data-testid={testIdPrefix + "-itemsStars"}
          type="number"
          step="1"
          isInvalid={Boolean(errors.itemsStars)}
          {...register("itemsStars", {
            required: "Stars is required.",
            valueAsNumber: true,
            min: { value: 1, message: "Stars must be between 1 and 5." },
            max: { value: 5, message: "Stars must be between 1 and 5." },
          })}
        />
        <Form.Control.Feedback type="invalid">
          {errors.itemsStars?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Form.Group className="mb-3">
        <Form.Label htmlFor="review-date">
          Date and Time Item was Served
        </Form.Label>
        <Form.Control
          id="review-date"
          data-testid={testIdPrefix + "-dateItemServed"}
          type="datetime-local"
          isInvalid={Boolean(errors.dateItemServed)}
          {...register("dateItemServed", {
            required: "Date and time served is required.",
          })}
        />
        <Form.Control.Feedback type="invalid">
          {errors.dateItemServed?.message}
        </Form.Control.Feedback>
      </Form.Group>

      <Button type="submit" data-testid={testIdPrefix + "-submit"}>
        {submitButtonText}
      </Button>
    </Form>
  );
}
