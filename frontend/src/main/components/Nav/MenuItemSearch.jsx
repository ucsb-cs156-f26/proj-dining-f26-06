import { useRef, useState } from "react";
import axios from "axios";
import { toast } from "react-toastify";
import { useNavigate } from "react-router";
import { AsyncTypeahead } from "react-bootstrap-typeahead";
import "react-bootstrap-typeahead/css/Typeahead.css";

export default function MenuItemSearch() {
  const navigate = useNavigate();
  const typeaheadRef = useRef();
  const latestQuery = useRef();
  const [isLoading, setIsLoading] = useState(false);
  const [options, setOptions] = useState([]);

  const handleSearch = async (query) => {
    const trimmedQuery = query.trim();
    latestQuery.current = trimmedQuery;
    setIsLoading(true);

    let results = [];
    let failed = false;
    try {
      const response = await axios.get("/api/diningcommons/menuitem/search", {
        params: { query: trimmedQuery },
      });
      results = response.data;
    } catch {
      failed = true;
    }

    // A newer search has started since this one; its response is the one to show.
    if (latestQuery.current !== trimmedQuery) {
      return;
    }
    if (failed) {
      toast("Error searching for menu items");
    }
    setOptions(results);
    setIsLoading(false);
  };

  const handleChange = (selected) => {
    navigate(`/reviews/${selected[0].id}`);
    typeaheadRef.current.clear();
  };

  return (
    <AsyncTypeahead
      ref={typeaheadRef}
      id="menu-item-search"
      labelKey="name"
      placeholder="Search menu items"
      inputProps={{ "aria-label": "Search menu items" }}
      isLoading={isLoading}
      options={options}
      onSearch={handleSearch}
      onChange={handleChange}
      filterBy={() => true}
      renderMenuItemChildren={(option) => (
        <>
          <span>{option.name}</span>
          <small className="ms-2 text-muted">{option.diningCommonsCode}</small>
        </>
      )}
    />
  );
}
