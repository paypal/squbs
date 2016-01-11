package org.squbs.httpclient.japi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public abstract class PagedResult<T> {

    private Integer startPage;

    protected List<T> pages;

    @JsonProperty("start_page")
    public Integer getStartPage() {
        return startPage;
    }

    public void setStartPage(Integer startPage) {
        this.startPage = startPage;
    }

    public abstract List<T> getPages();

    public final void setPages(List<T> pages) {
        this.pages = pages;
    }
}
