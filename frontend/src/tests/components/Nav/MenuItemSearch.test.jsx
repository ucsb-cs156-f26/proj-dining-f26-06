import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import axios from "axios";
import AxiosMockAdapter from "axios-mock-adapter";

import MenuItemSearch from "main/components/Nav/MenuItemSearch";
import { menuItemSearchFixtures } from "fixtures/menuItemSearchFixtures";

const mockedNavigate = vi.fn();
vi.mock("react-router", async () => ({
  ...(await vi.importActual("react-router")),
  useNavigate: () => mockedNavigate,
}));

const mockToast = vi.fn();
vi.mock("react-toastify", async () => {
  const originalModule = await vi.importActual("react-toastify");
  return {
    __esModule: true,
    ...originalModule,
    toast: (x) => mockToast(x),
  };
});

describe("MenuItemSearch tests", () => {
  const axiosMock = new AxiosMockAdapter(axios);
  const searchUrl = "/api/diningcommons/menuitem/search";

  beforeEach(() => {
    axiosMock.reset();
    axiosMock.resetHistory();
    mockedNavigate.mockClear();
    mockToast.mockClear();
  });

  const renderComponent = () =>
    render(
      <MemoryRouter>
        <MenuItemSearch />
      </MemoryRouter>,
    );

  test("shows each result with its name and dining commons", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.threeResults);
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sliced");

    expect(await screen.findByText("Sliced Pastrami")).toBeInTheDocument();
    expect(screen.getByText("carrillo")).toBeInTheDocument();
    expect(mockToast).not.toHaveBeenCalled();
    expect(screen.getAllByText("Sliced Turkey")).toHaveLength(2);
    expect(screen.getByText("de-la-guerra")).toBeInTheDocument();
    expect(screen.getByText("ortega")).toBeInTheDocument();
  });

  test("sends the trimmed query to the backend", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.threeResults);
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sliced  ");

    // Slow typing can trigger an earlier search (e.g. for "sli"), so wait for the final one.
    await waitFor(() =>
      expect(axiosMock.history.get.at(-1)?.params).toEqual({ query: "sliced" }),
    );
    axiosMock.history.get.forEach((request) => {
      expect(request.url).toBe(searchUrl);
      expect(request.params.query).toBe(request.params.query.trim());
    });
  });

  test("navigates to the reviews page of the selected item", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.threeResults);
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sliced");
    await userEvent.click(await screen.findByText("Sliced Pastrami"));

    expect(mockedNavigate).toHaveBeenCalledTimes(1);
    expect(mockedNavigate).toHaveBeenCalledWith("/reviews/11");
  });

  test("navigates to the right item when names are duplicated", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.threeResults);
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sliced");
    await userEvent.click(await screen.findByText("ortega"));

    expect(mockedNavigate).toHaveBeenCalledWith("/reviews/31");
  });

  test("clears the input after navigating, without navigating again", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.threeResults);
    renderComponent();

    const input = screen.getByRole("combobox");
    await userEvent.type(input, "sliced");
    await userEvent.click(await screen.findByText("Sliced Pastrami"));

    expect(input).toHaveValue("");
    expect(mockedNavigate).toHaveBeenCalledTimes(1);
  });

  test("has an accessible label", () => {
    renderComponent();

    expect(screen.getByLabelText("Search menu items")).toBeInTheDocument();
  });

  test("ignores an older response that arrives while a newer search is still pending", async () => {
    const olderResults = [
      { id: 99, name: "Older Result", diningCommonsCode: "portola" },
    ];
    const respondAfter = (delay, results) =>
      new Promise((resolve) =>
        setTimeout(() => resolve([200, results]), delay),
      );
    axiosMock.onGet(searchUrl).reply((config) => {
      if (config.params.query === "sl") {
        return respondAfter(500, olderResults);
      }
      return respondAfter(1200, menuItemSearchFixtures.threeResults);
    });
    renderComponent();

    const input = screen.getByRole("combobox");
    await userEvent.type(input, "sl");
    await screen.findByText("Searching...");
    await userEvent.type(input, "i");

    // Wait until the older "sl" response has come back, but not the "sli" one.
    await new Promise((resolve) => setTimeout(resolve, 800));
    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
    expect(screen.getByText("Searching...")).toBeInTheDocument();

    expect(
      await screen.findByText("Sliced Pastrami", {}, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Older Result")).not.toBeInTheDocument();
  });

  test("shows no menu items when nothing matches", async () => {
    axiosMock.onGet(searchUrl).reply(200, menuItemSearchFixtures.noResults);
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "zzzz");

    expect(await screen.findByText("No matches found.")).toBeInTheDocument();
    expect(screen.getAllByRole("option")).toHaveLength(1);
  });

  test("prompts, then shows a searching message, then the results", async () => {
    axiosMock
      .onGet(searchUrl)
      .reply(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () => resolve([200, menuItemSearchFixtures.threeResults]),
              500,
            ),
          ),
      );
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sl");
    expect(screen.getByText("Type to search...")).toBeInTheDocument();

    expect(await screen.findByText("Searching...")).toBeInTheDocument();
    expect(await screen.findByText("Sliced Pastrami")).toBeInTheDocument();
    expect(screen.queryByText("Searching...")).not.toBeInTheDocument();
  });

  test("shows a toast and no results when the search fails", async () => {
    axiosMock.onGet(searchUrl).networkError();
    renderComponent();

    await userEvent.type(screen.getByRole("combobox"), "sliced");

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith("Error searching for menu items");
    });
    expect(await screen.findByText("No matches found.")).toBeInTheDocument();
    expect(screen.queryByText("Sliced Pastrami")).not.toBeInTheDocument();
  });
});
