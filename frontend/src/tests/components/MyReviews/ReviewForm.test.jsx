import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import ReviewForm from "main/components/MyReviews/ReviewForm";
import { vi } from "vitest";

describe("ReviewForm tests", () => {
  test("renders provided initial values and trims datetime-local seconds", () => {
    render(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="Great texture"
        initialStars={4}
        initialDateServed="2024-04-01T12:34:56"
        submitAction={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Item Name")).toHaveValue("Pasta");
    expect(screen.getByLabelText("Item Name")).toBeDisabled();
    expect(screen.getByLabelText("Comments")).toHaveValue("Great texture");
    expect(screen.getByLabelText("Stars (1 to 5)")).toHaveValue(4);
    expect(screen.getByLabelText("Date and Time Item was Served")).toHaveValue(
      "2024-04-01T12:34",
    );
  });

  test("updates form state when initial values change", () => {
    const submitAction = vi.fn();
    const { rerender } = render(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="First comment"
        initialStars={2}
        initialDateServed="2024-04-01T12:34:56"
        submitAction={submitAction}
      />,
    );

    fireEvent.change(screen.getByLabelText("Comments"), {
      target: { value: "Locally edited comment" },
    });
    fireEvent.change(screen.getByLabelText("Stars (1 to 5)"), {
      target: { value: "3" },
    });
    fireEvent.change(screen.getByLabelText("Date and Time Item was Served"), {
      target: { value: "2024-04-03T16:20" },
    });

    rerender(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="Updated from backend"
        initialStars={5}
        initialDateServed="2024-04-02T13:45:59"
        submitAction={submitAction}
      />,
    );

    expect(screen.getByLabelText("Comments")).toHaveValue(
      "Updated from backend",
    );
    expect(screen.getByLabelText("Stars (1 to 5)")).toHaveValue(5);
    expect(screen.getByLabelText("Date and Time Item was Served")).toHaveValue(
      "2024-04-02T13:45",
    );
  });

  test("does not overwrite the current date when no new initial date is provided", () => {
    const submitAction = vi.fn();
    const { rerender } = render(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="First comment"
        initialStars={2}
        initialDateServed="2024-04-01T12:34:56"
        submitAction={submitAction}
      />,
    );

    fireEvent.change(screen.getByLabelText("Date and Time Item was Served"), {
      target: { value: "2024-04-03T16:20" },
    });

    rerender(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="Updated from backend"
        initialStars={5}
        submitAction={submitAction}
      />,
    );

    expect(screen.getByLabelText("Comments")).toHaveValue(
      "Updated from backend",
    );
    expect(screen.getByLabelText("Stars (1 to 5)")).toHaveValue(5);
    expect(screen.getByLabelText("Date and Time Item was Served")).toHaveValue(
      "2024-04-03T16:20",
    );
  });

  test("submits the expected values, with the date as a plain string", async () => {
    const submitAction = vi.fn();
    render(<ReviewForm initialItemName="Pasta" submitAction={submitAction} />);

    fireEvent.change(screen.getByLabelText("Comments"), {
      target: { value: "Tasty" },
    });
    fireEvent.change(screen.getByLabelText("Stars (1 to 5)"), {
      target: { value: "4" },
    });
    fireEvent.change(screen.getByLabelText("Date and Time Item was Served"), {
      target: { value: "2024-04-01T12:34" },
    });
    fireEvent.click(screen.getByTestId("ReviewForm-submit"));

    await waitFor(() => expect(submitAction).toHaveBeenCalledTimes(1));
    expect(submitAction.mock.calls[0][0]).toEqual({
      reviewerComments: "Tasty",
      itemsStars: 4,
      dateItemServed: "2024-04-01T12:34",
    });
  });

  test("shows an error when stars is empty", async () => {
    const submitAction = vi.fn();
    render(<ReviewForm submitAction={submitAction} />);

    fireEvent.change(screen.getByLabelText("Stars (1 to 5)"), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByTestId("ReviewForm-submit"));

    expect(await screen.findByText("Stars is required.")).toBeInTheDocument();
    expect(submitAction).not.toHaveBeenCalled();
  });

  test("shows an error when stars is below 1", async () => {
    const submitAction = vi.fn();
    render(<ReviewForm submitAction={submitAction} />);

    fireEvent.change(screen.getByLabelText("Stars (1 to 5)"), {
      target: { value: "0" },
    });
    fireEvent.click(screen.getByTestId("ReviewForm-submit"));

    expect(
      await screen.findByText("Stars must be between 1 and 5."),
    ).toBeInTheDocument();
    expect(submitAction).not.toHaveBeenCalled();
  });

  test("shows an error when stars is above 5", async () => {
    const submitAction = vi.fn();
    render(<ReviewForm submitAction={submitAction} />);

    fireEvent.change(screen.getByLabelText("Stars (1 to 5)"), {
      target: { value: "6" },
    });
    fireEvent.click(screen.getByTestId("ReviewForm-submit"));

    expect(
      await screen.findByText("Stars must be between 1 and 5."),
    ).toBeInTheDocument();
    expect(submitAction).not.toHaveBeenCalled();
  });

  test("shows an error when the date is cleared", async () => {
    const submitAction = vi.fn();
    render(<ReviewForm submitAction={submitAction} />);

    fireEvent.change(screen.getByLabelText("Date and Time Item was Served"), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByTestId("ReviewForm-submit"));

    expect(
      await screen.findByText("Date and time served is required."),
    ).toBeInTheDocument();
    expect(submitAction).not.toHaveBeenCalled();
  });

  test("renders every field with its test id", () => {
    render(
      <ReviewForm
        initialItemName="Pasta"
        initialComments="Great texture"
        initialStars={4}
        initialDateServed="2024-04-01T12:34:56"
        submitAction={vi.fn()}
      />,
    );

    expect(screen.getByTestId("ReviewForm-itemName")).toHaveValue("Pasta");
    expect(screen.getByTestId("ReviewForm-reviewerComments")).toHaveValue(
      "Great texture",
    );
    expect(screen.getByTestId("ReviewForm-itemsStars")).toHaveValue(4);
    expect(screen.getByTestId("ReviewForm-itemsStars")).toHaveAttribute(
      "step",
      "1",
    );
    expect(screen.getByTestId("ReviewForm-dateItemServed")).toHaveValue(
      "2024-04-01T12:34",
    );
    expect(screen.getByTestId("ReviewForm-submit")).toHaveTextContent(
      "Submit Review",
    );
  });
});
