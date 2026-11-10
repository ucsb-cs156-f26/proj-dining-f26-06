import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "react-query";
import { MemoryRouter } from "react-router";
import AxiosMockAdapter from "axios-mock-adapter";
import axios from "axios";
import UsersIndexPage from "main/pages/Admin/UsersIndexPage";
import { apiCurrentUserFixtures } from "fixtures/currentUserFixtures";
import { systemInfoFixtures } from "fixtures/systemInfoFixtures";
import usersFixtures from "fixtures/usersFixtures";

describe("UsersIndexPage tests", () => {
  const axiosMock = new AxiosMockAdapter(axios);

  const setup = () => {
    axiosMock.reset();
    axiosMock.resetHistory();
    axiosMock
      .onGet("/api/currentUser")
      .reply(200, apiCurrentUserFixtures.adminUser);
    axiosMock
      .onGet("/api/systemInfo")
      .reply(200, systemInfoFixtures.showingNeither);
  };

  // Mimics the backend: returns the requested slice of `users` as a Page object
  const mockPagedBackend = (users) => {
    axiosMock.onGet("/api/admin/users/paged").reply((config) => {
      const page = config.params?.page ?? 0;
      const size = config.params?.size ?? 10;
      return [
        200,
        {
          content: users.slice(page * size, (page + 1) * size),
          totalElements: users.length,
          totalPages: Math.ceil(users.length / size),
          number: page,
          size: size,
        },
      ];
    });
  };

  const makeUsers = (count) =>
    Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      email: `user${i + 1}@ucsb.edu`,
      givenName: `First${i + 1}`,
      familyName: `Last${i + 1}`,
      admin: false,
      moderator: false,
    }));

  const renderPage = () => {
    const queryClient = new QueryClient();
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <UsersIndexPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
  };

  const expectPageInfo = async (text) => {
    await waitFor(() =>
      expect(screen.getByTestId("UsersIndexPage-page-info")).toHaveTextContent(
        text,
      ),
    );
  };

  test("renders without crashing", async () => {
    setup();
    mockPagedBackend([]);
    renderPage();

    expect(await screen.findByText("Users")).toBeInTheDocument();
  });

  test("renders three users in the table", async () => {
    setup();
    mockPagedBackend(usersFixtures.threeUsers);
    renderPage();

    expect(await screen.findByText("phtcon@ucsb.edu")).toBeInTheDocument();
    expect(screen.getByText("pconrad.cis@gmail.com")).toBeInTheDocument();
    expect(screen.getByText("craig.zzyzx@example.org")).toBeInTheDocument();
  });

  test("requests the paged endpoint with page and size", async () => {
    setup();
    mockPagedBackend(usersFixtures.threeUsers);
    renderPage();

    await screen.findByText("phtcon@ucsb.edu");

    const pagedCalls = axiosMock.history.get.filter(
      (req) => req.url === "/api/admin/users/paged",
    );
    expect(pagedCalls.length).toBeGreaterThan(0);
    expect(pagedCalls[0].params).toEqual({ page: 0, size: 10 });
  });

  test("page size selector has correct options", async () => {
    setup();
    mockPagedBackend([]);
    renderPage();

    const select = await screen.findByTestId("UsersIndexPage-pageSize");
    expect(select).toBeInTheDocument();

    const options = select.querySelectorAll("option");
    const values = Array.from(options).map((o) => o.value);
    expect(values).toEqual(["10", "25", "50", "100", "500"]);
  });

  test("paging works correctly", async () => {
    setup();
    mockPagedBackend(makeUsers(15));
    renderPage();

    await expectPageInfo("Page 1 of 2");

    // first page shows user1 but not user11
    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();
    expect(screen.queryByText("user11@ucsb.edu")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));

    expect(await screen.findByText("user11@ucsb.edu")).toBeInTheDocument();
    await expectPageInfo("Page 2 of 2");
    expect(screen.queryByText("user1@ucsb.edu")).not.toBeInTheDocument();

    // the second request asked the backend for page 1 (zero-based)
    const pagedCalls = axiosMock.history.get.filter(
      (req) => req.url === "/api/admin/users/paged",
    );
    expect(pagedCalls.some((req) => req.params.page === 1)).toBe(true);

    fireEvent.click(screen.getByTestId("UsersIndexPage-prev"));
    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();
    await expectPageInfo("Page 1 of 2");
  });

  test("prev and next buttons are disabled with a single page", async () => {
    setup();
    mockPagedBackend(usersFixtures.threeUsers);
    renderPage();

    await screen.findByText("phtcon@ucsb.edu");
    expect(screen.getByTestId("UsersIndexPage-prev")).toBeDisabled();
    expect(screen.getByTestId("UsersIndexPage-next")).toBeDisabled();
  });

  test("prev from page 3 goes to page 2 (not page 1)", async () => {
    setup();
    // 21 users give 3 pages at the default page size of 10
    mockPagedBackend(makeUsers(21));
    renderPage();

    await expectPageInfo("Page 1 of 3");
    await screen.findByText("user1@ucsb.edu");

    // advance to page 3, waiting for each page to load before clicking again
    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));
    await screen.findByText("user11@ucsb.edu");
    await expectPageInfo("Page 2 of 3");

    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));
    expect(await screen.findByText("user21@ucsb.edu")).toBeInTheDocument();
    await expectPageInfo("Page 3 of 3");

    // prev from page 3 should go to page 2, not page 1
    fireEvent.click(screen.getByTestId("UsersIndexPage-prev"));
    expect(await screen.findByText("user11@ucsb.edu")).toBeInTheDocument();
    await expectPageInfo("Page 2 of 3");
    expect(screen.queryByText("user1@ucsb.edu")).not.toBeInTheDocument();
    expect(screen.queryByText("user21@ucsb.edu")).not.toBeInTheDocument();
  });

  test("changing page size resets to first page", async () => {
    setup();
    mockPagedBackend(makeUsers(15));
    renderPage();

    await expectPageInfo("Page 1 of 2");
    await screen.findByText("user1@ucsb.edu");

    // go to page 2
    fireEvent.click(screen.getByTestId("UsersIndexPage-next"));
    await screen.findByText("user11@ucsb.edu");
    await expectPageInfo("Page 2 of 2");

    // change page size to 25 (all 15 fit on one page)
    fireEvent.change(screen.getByTestId("UsersIndexPage-pageSize"), {
      target: { value: "25" },
    });

    await expectPageInfo("Page 1 of 1");
    expect(await screen.findByText("user1@ucsb.edu")).toBeInTheDocument();
    expect(screen.getByText("user15@ucsb.edu")).toBeInTheDocument();

    const pagedCalls = axiosMock.history.get.filter(
      (req) => req.url === "/api/admin/users/paged",
    );
    expect(
      pagedCalls.some((req) => req.params.page === 0 && req.params.size === 25),
    ).toBe(true);
  });
});
