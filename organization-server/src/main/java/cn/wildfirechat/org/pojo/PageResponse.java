package cn.wildfirechat.org.pojo;

import java.util.List;

public class PageResponse<T> {
    public int totalPages;
    public int totalCount;
    public int currentPage;
    public List<T> contents;
}
