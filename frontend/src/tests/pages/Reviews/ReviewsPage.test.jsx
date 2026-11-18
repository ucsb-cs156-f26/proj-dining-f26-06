import { render, waitFor, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter, Route, Routes } from "react-router";
import ReviewsPage from "main/pages/Reviews/ReviewsPage";
import { vi } from "vitest";

import { apiCurrentUserFixtures } from "fixtures/currentUserFixtures";
import { systemInfoFixtures } from "fixtures/systemInfoFixtures";
import { ReviewFixtures } from "fixtures/reviewFixtures";
import { menuItemFixtures } from "fixtures/menuItemFixtures";
import axios from "axios";
import AxiosMockAdapter from "axios-mock-adapter";

const mockToast = vi.fn();
vi.mock("react-toastify", async () => {
  const originalModule = await vi.importActual("react-toastify");
  return {
    __esModule: true,
    ...originalModule,
    toast: (x) => mockToast(x),
  };
});

const axiosMock = new AxiosMockAdapter(axios);
describe("ReviewsPage tests", () => {
  const testId = "Reviewstable";

  const setupMenuItem = (itemid) => {
    axiosMock
      .onGet(`/api/diningcommons/menuitem?id=${itemid}`)
      .reply(200, menuItemFixtures.menuItemWithCommons);
  };

  const setupUserOnly = () => {
    axiosMock
      .onGet("/api/currentUser")
      .reply(200, apiCurrentUserFixtures.userOnly);
  };

  beforeEach(() => {
    axiosMock.reset();
    axiosMock.resetHistory();
    axiosMock
      .onGet("/api/systemInfo")
      .reply(200, systemInfoFixtures.showingNeither);
  });
  afterEach(() => {
    axiosMock.reset();
    axiosMock.resetHistory();
  });

  const renderWithRoute = (itemid) => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[`/reviews/${itemid}`]}>
          <Routes>
            <Route path="/reviews/:itemid" element={<ReviewsPage />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  };

  test("renders empty table when backend is unavailable", async () => {
    const itemid = "7";
    setupUserOnly();
    axiosMock.onGet(`/api/reviews/approved/forItem/${itemid}`).timeout();
    setupMenuItem(itemid);

    // SPY ON THE ADAPTER, NOT axios.request
    const adapterSpy = vi.spyOn(axios.defaults, "adapter");

    renderWithRoute(itemid);

    await waitFor(() => {
      expect(adapterSpy).toHaveBeenCalled();
      expect(adapterSpy.mock.calls[0][0].method).toBe("get"); // MUTATION KILLER
    });

    await waitFor(() => {
      // Table should remain empty
      expect(
        screen.queryByTestId(`${testId}-cell-row-0-col-item.id`),
      ).not.toBeInTheDocument();
    });
  });

  test("renders table when backend is available", async () => {
    const itemid = "7";
    setupUserOnly();
    const adapterSpy = vi.spyOn(axios.defaults, "adapter");
    axiosMock
      .onGet(`/api/reviews/approved/forItem/${itemid}`)
      .reply(200, ReviewFixtures.threeReviews);
    setupMenuItem(itemid);

    renderWithRoute(itemid);

    await waitFor(() => {
      expect(adapterSpy).toHaveBeenCalled();
      expect(adapterSpy.mock.calls[0][0].method).toBe("get");
    });

    await waitFor(() => {
      expect(
        screen.getByTestId(`${testId}-cell-row-0-col-item.id`),
      ).toBeInTheDocument();
    });
  });

  test("Error message includes GET method when request fails", async () => {
    const itemId = "7";
    setupUserOnly();

    axiosMock.onGet(`/api/reviews/approved/forItem/${itemId}`).networkError();
    setupMenuItem(itemId);

    renderWithRoute(itemId);

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalled();
      const toastMessage = mockToast.mock.calls[0][0];
      expect(toastMessage).toContain("GET");
      expect(toastMessage).toContain(`/api/reviews/approved/forItem/${itemId}`);
    });
  });

  test("shows the item name, dining commons, meal and station", async () => {
    const itemid = "7";
    setupUserOnly();
    setupMenuItem(itemid);
    axiosMock
      .onGet(`/api/reviews/approved/forItem/${itemid}`)
      .reply(200, ReviewFixtures.threeReviews);

    renderWithRoute(itemid);

    expect(
      await screen.findByRole("heading", {
        name: "Reviews for Sliced Pastrami",
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("ReviewsPage-commons")).toHaveTextContent(
      /^Served at carrillo for lunch, Deli station$/,
    );
  });

  test("falls back to the item id when the item cannot be loaded", async () => {
    const itemid = "7";
    setupUserOnly();
    axiosMock.onGet(`/api/diningcommons/menuitem?id=${itemid}`).timeout();
    axiosMock
      .onGet(`/api/reviews/approved/forItem/${itemid}`)
      .reply(200, ReviewFixtures.threeReviews);

    renderWithRoute(itemid);

    expect(
      await screen.findByRole("heading", { name: "Reviews for Menu Item 7" }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("ReviewsPage-commons")).not.toBeInTheDocument();
  });

  test("shows a review button linking to the post review page for logged in users", async () => {
    const itemid = "7";
    setupUserOnly();
    setupMenuItem(itemid);
    axiosMock.onGet(`/api/reviews/approved/forItem/${itemid}`).reply(200, []);

    renderWithRoute(itemid);

    const button = await screen.findByTestId("ReviewsPage-review-button");
    expect(button).toHaveTextContent("Review this item");
    expect(button).toHaveAttribute("href", "/reviews/post/7");
  });

  test("does not show a review button when not logged in", async () => {
    const itemid = "7";
    axiosMock.onGet("/api/currentUser").reply(403);
    setupMenuItem(itemid);
    axiosMock.onGet(`/api/reviews/approved/forItem/${itemid}`).reply(200, []);

    renderWithRoute(itemid);

    await screen.findByRole("heading", { name: "Reviews for Sliced Pastrami" });
    expect(
      screen.queryByTestId("ReviewsPage-review-button"),
    ).not.toBeInTheDocument();
  });
});
