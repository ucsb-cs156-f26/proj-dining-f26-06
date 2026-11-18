import React from "react";
import { http, HttpResponse } from "msw";
import MenuItemSearch from "main/components/Nav/MenuItemSearch";
import { menuItemSearchFixtures } from "fixtures/menuItemSearchFixtures";

export default {
  title: "components/Nav/MenuItemSearch",
  component: MenuItemSearch,
};

const Template = (args) => {
  return <MenuItemSearch {...args} />;
};

export const WithResults = Template.bind({});
WithResults.parameters = {
  msw: [
    http.get("/api/diningcommons/menuitem/search", () => {
      return HttpResponse.json(menuItemSearchFixtures.threeResults, {
        status: 200,
      });
    }),
  ],
};

export const NoResults = Template.bind({});
NoResults.parameters = {
  msw: [
    http.get("/api/diningcommons/menuitem/search", () => {
      return HttpResponse.json(menuItemSearchFixtures.noResults, {
        status: 200,
      });
    }),
  ],
};
