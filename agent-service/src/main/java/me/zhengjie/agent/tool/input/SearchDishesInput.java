package me.zhengjie.agent.tool.input;

/** 搜索菜品和配料摘要的强类型输入。 */
public class SearchDishesInput {
    private String name;
    private DishType dishType;
    private Boolean enabled;
    private Integer page = 1;
    private Integer size = 20;

    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public DishType getDishType() { return dishType; }
    public void setDishType(DishType value) { dishType = value; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean value) { enabled = value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page = value; }
    public Integer getSize() { return size; }
    public void setSize(Integer value) { size = value; }
}
