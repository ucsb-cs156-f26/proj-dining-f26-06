import { Button } from "react-bootstrap";
import { Link, useParams } from "react-router";
import { useBackend } from "main/utils/useBackend";
import { hasRole, useCurrentUser } from "main/utils/currentUser";
import BasicLayout from "main/layouts/BasicLayout/BasicLayout";
import ReviewsTable from "main/components/Reviews/ReviewsTable";

export default function ReviewsPage() {
  const { itemid } = useParams();
  const { data: currentUser } = useCurrentUser();
  const {
    data: reviews,
    error: _error,
    status: _status,
  } = useBackend(
    // Stryker disable next-line all : don't test internal caching of React Query
    [`/api/reviews/approved/forItem/${itemid}`],
    // Stryker disable next-line all : don't test internal caching of React Query
    { method: "GET", url: `/api/reviews/approved/forItem/${itemid}` },
    [],
  );
  const { data: menuItem } = useBackend(
    // Stryker disable next-line all : don't test internal caching of React Query
    [`/api/diningcommons/menuitem?id=${itemid}`],
    // Stryker disable next-line all : don't test internal caching of React Query
    { method: "GET", url: `/api/diningcommons/menuitem?id=${itemid}` },
  );

  return (
    <BasicLayout>
      <div className="pt-2">
        <h1>Reviews for {menuItem ? menuItem.name : `Menu Item ${itemid}`}</h1>
        {menuItem && (
          <p data-testid="ReviewsPage-commons">
            Served at {menuItem.diningCommonsCode} for {menuItem.mealCode},{" "}
            {menuItem.station} station
          </p>
        )}
        {hasRole(currentUser, "ROLE_USER") && (
          <Button
            as={Link}
            to={`/reviews/post/${itemid}`}
            data-testid="ReviewsPage-review-button"
            className="mb-3"
          >
            Review this item
          </Button>
        )}
        <ReviewsTable reviews={reviews} />
      </div>
    </BasicLayout>
  );
}
