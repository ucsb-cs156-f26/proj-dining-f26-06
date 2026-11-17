import { useCurrentUser } from "main/utils/currentUser";
import { useBackend } from "main/utils/useBackend";
import BasicLayout from "main/layouts/BasicLayout/BasicLayout";
import MenuItemTable from "main/components/MenuItem/MenuItemTable";
import { useParams } from "react-router";
//"Capitalize the dining column name, e.g., 'de-la-guerra' → 'De La Guerra'"
const formatCommons = (slug) =>
  slug
    .split("-")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");

// Converts a date string to its full readable form: "2026-11-14" -> "Saturday, November 14, 2026"
const formatDate = (dateStr) => {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(y, m - 1, d).toLocaleDateString("en-US", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });
};
export default function MenuItemPage() {
  const currentUser = useCurrentUser();
  const {
    "date-time": date,
    "dining-commons-code": diningCommons,
    meal,
  } = useParams();
  const { data: menuItems } = useBackend(
    // Stryker disable next-line all : don't test internal caching of React Query
    [`/api/diningcommons/${date}/${diningCommons}/${meal}`],
    {
      // Stryker disable next-line all : the default method is get, so replacing with an empty string will do nothing
      method: "GET",
      url: `/api/diningcommons/${date}/${diningCommons}/${meal}`,
    },
    // Stryker disable next-line all : Don't test empty initial data
    [],
  );

  return (
    <BasicLayout>
      <h2>{formatCommons(diningCommons)}</h2>
      <h4>
        {meal.at(0).toUpperCase() + meal.substring(1)} · {formatDate(date)}
      </h4>
      <MenuItemTable currentUser={currentUser} menuItems={menuItems} />
    </BasicLayout>
  );
}
