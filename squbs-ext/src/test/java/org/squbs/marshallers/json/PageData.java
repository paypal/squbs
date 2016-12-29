package org.squbs.marshallers.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PageData extends PagedResult<String> {

    public PageData(Integer startPage, List<String> pages) {
        setStartPage(startPage);
        setPages(pages);
    }

    public PageData() {
    }

    @Override
    @JsonProperty("data_pages")
    public List<String> getPages() {
        return pages;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PageData) {
            PageData p = (PageData) obj;
            if (pages.equals(p.getPages()) && getStartPage().equals(p.getStartPage())) {
                return true;
            }
        }
        return false;
    }
}
