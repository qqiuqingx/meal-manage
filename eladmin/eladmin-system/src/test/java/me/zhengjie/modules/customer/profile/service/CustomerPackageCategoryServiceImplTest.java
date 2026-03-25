package me.zhengjie.modules.customer.profile.service;

import me.zhengjie.modules.customer.profile.domain.CustomerPackageCategory;
import me.zhengjie.modules.customer.profile.mapper.CustomerPackageCategoryMapper;
import me.zhengjie.modules.customer.profile.service.impl.CustomerPackageCategoryServiceImpl;
import me.zhengjie.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 套餐分类服务测试 - TDD approach
 *
 * 本测试文件在实现前编写，用于定义期望的业务行为。
 * 测试失败是正常的，表示功能尚未实现。
 */
@ExtendWith(MockitoExtension.class)
class CustomerPackageCategoryServiceImplTest {

    @Mock
    private CustomerPackageCategoryMapper categoryMapper;

    @InjectMocks
    private CustomerPackageCategoryServiceImpl service;

    private CustomerPackageCategory parentCategory;
    private CustomerPackageCategory childCategory;

    @BeforeEach
    void setUp() {
        // 父级套餐
        parentCategory = new CustomerPackageCategory();
        parentCategory.setId(1L);
        parentCategory.setCategoryName("月子餐");
        parentCategory.setCategoryCode("PACKAGE_A");
        parentCategory.setParentId(null);
        parentCategory.setLevel(1);
        parentCategory.setEnabled(true);
        parentCategory.setCodePrefix("A");

        // 子级套餐
        childCategory = new CustomerPackageCategory();
        childCategory.setId(2L);
        childCategory.setCategoryName("两荤一素");
        childCategory.setCategoryCode("PACKAGE_A_1");
        childCategory.setParentId(1L);
        childCategory.setLevel(2);
        childCategory.setEnabled(true);
        childCategory.setCodePrefix(null);
    }

    /**
     * Test: shouldRejectDuplicateCodePrefixForEnabledParent
     * 场景: 尝试创建一个新的父级套餐，使用已存在的启用状态的编号前缀
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectDuplicateCodePrefixForEnabledParent() {
        // Arrange: 创建一个新的父级套餐，使用已存在的"A"前缀
        CustomerPackageCategory newParent = new CustomerPackageCategory();
        newParent.setCategoryName("新套餐");
        newParent.setCategoryCode("PACKAGE_C");
        newParent.setLevel(1);
        newParent.setCodePrefix("A"); // 已存在的启用前缀

        // Mock: 模拟数据库查询，prefix "A" 已存在且启用
        when(categoryMapper.existsEnabledParentPrefix("A", null)).thenReturn(true);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.create(newParent));

        assertEquals("编号前缀 A 已存在", exception.getMessage());
        verify(categoryMapper).existsEnabledParentPrefix("A", null);
    }

    /**
     * Test: shouldRejectChildBoundToDisabledParent
     * 场景: 尝试创建一个子级套餐，但父级套餐已被禁用
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectChildBoundToDisabledParent() {
        // Arrange: 创建一个子级套餐，父级ID为1
        CustomerPackageCategory newChild = new CustomerPackageCategory();
        newChild.setCategoryName("新子套餐");
        newChild.setCategoryCode("PACKAGE_A_4");
        newChild.setParentId(1L);
        newChild.setLevel(2);

        // Mock: 父级套餐已禁用
        CustomerPackageCategory disabledParent = new CustomerPackageCategory();
        disabledParent.setId(1L);
        disabledParent.setEnabled(false);

        when(categoryMapper.selectById(1L)).thenReturn(disabledParent);

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.create(newChild));

        assertEquals("父级套餐已禁用，无法添加子级", exception.getMessage());
    }

    /**
     * Test: shouldAllowChildUnderEnabledParent
     * 场景: 创建一个子级套餐，父级套餐启用状态正常
     * 期望: 允许创建
     */
    @Test
    void shouldAllowChildUnderEnabledParent() {
        // Arrange: 创建子级套餐，父级启用
        CustomerPackageCategory newChild = new CustomerPackageCategory();
        newChild.setCategoryName("新子套餐");
        newChild.setCategoryCode("PACKAGE_A_4");
        newChild.setParentId(1L);
        newChild.setLevel(2);

        // Mock: 父级启用
        when(categoryMapper.selectById(1L)).thenReturn(parentCategory);
        when(categoryMapper.insert(any(CustomerPackageCategory.class))).thenReturn(1);

        // Act
        service.create(newChild);

        // Assert
        verify(categoryMapper).insert(any(CustomerPackageCategory.class));
    }

    /**
     * Test: shouldRejectDeleteParentWithChildren
     * 场景: 尝试删除有子节点的父级套餐
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectDeleteParentWithChildren() {
        // Arrange: 删除父级ID=1
        when(categoryMapper.selectById(1L)).thenReturn(parentCategory);
        when(categoryMapper.countChildren(1L)).thenReturn(3); // 有3个子节点

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.delete(1L));

        assertEquals("该分类下存在子分类，无法删除", exception.getMessage());
    }

    /**
     * Test: shouldRejectDeleteCategoryReferencedByCustomer
     * 场景: 尝试删除已被客户引用的套餐分类
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectDeleteCategoryReferencedByCustomer() {
        // Arrange: 删除子级套餐ID=2
        when(categoryMapper.selectById(2L)).thenReturn(childCategory);
        when(categoryMapper.countChildren(2L)).thenReturn(0); // 无子节点
        when(categoryMapper.countCustomerReferences(2L)).thenReturn(5); // 被5个客户引用

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.delete(2L));

        assertEquals("该分类已被客户引用，无法删除", exception.getMessage());
    }

    /**
     * Test: shouldAllowDeleteUnreferencedCategory
     * 场景: 删除未被引用的分类
     * 期望: 允许删除
     */
    @Test
    void shouldAllowDeleteUnreferencedCategory() {
        // Arrange: 删除子级套餐ID=2
        when(categoryMapper.selectById(2L)).thenReturn(childCategory);
        when(categoryMapper.countChildren(2L)).thenReturn(0);
        when(categoryMapper.countCustomerReferences(2L)).thenReturn(0);
        when(categoryMapper.deleteById(2L)).thenReturn(1);

        // Act
        service.delete(2L);

        // Assert
        verify(categoryMapper).deleteById(2L);
    }

    /**
     * Test: shouldBuildTreeWithChildren
     * 场景: 查询分类树，包含父级和子级
     * 期望: 返回树形结构
     */
    @Test
    void shouldBuildTreeWithChildren() {
        // Arrange
        List<CustomerPackageCategory> allCategories = Arrays.asList(parentCategory, childCategory);
        when(categoryMapper.findAllOrderBySort()).thenReturn(allCategories);

        // Act
        List<CustomerPackageCategory> tree = service.getTree();

        // Assert
        assertEquals(1, tree.size());
        assertEquals("月子餐", tree.get(0).getCategoryName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("两荤一素", tree.get(0).getChildren().get(0).getCategoryName());
    }

    /**
     * Test: shouldReturnOnlyEnabledParents
     * 场景: 获取父级列表
     * 期望: 只返回启用状态的父级
     */
    @Test
    void shouldReturnOnlyEnabledParents() {
        // Arrange
        CustomerPackageCategory disabledParent = new CustomerPackageCategory();
        disabledParent.setId(3L);
        disabledParent.setCategoryName("已禁用套餐");
        disabledParent.setLevel(1);
        disabledParent.setEnabled(false);

        when(categoryMapper.findEnabledParents()).thenReturn(Collections.singletonList(parentCategory));

        // Act
        List<CustomerPackageCategory> parents = service.getParents();

        // Assert
        assertEquals(1, parents.size());
        assertTrue(parents.get(0).getEnabled());
    }

    /**
     * Test: shouldRejectParentWithoutCodePrefix
     * 场景: 创建父级套餐但未设置编号前缀
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectParentWithoutCodePrefix() {
        // Arrange
        CustomerPackageCategory newParent = new CustomerPackageCategory();
        newParent.setCategoryName("新套餐");
        newParent.setCategoryCode("PACKAGE_C");
        newParent.setLevel(1);
        newParent.setCodePrefix(null); // 缺少前缀

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.create(newParent));

        assertEquals("父级套餐必须设置编号前缀", exception.getMessage());
    }

    /**
     * Test: shouldRejectCodePrefixNotSingleUppercase
     * 场景: 编号前缀不是单个大写字母
     * 期望: 抛出 BadRequestException
     */
    @Test
    void shouldRejectCodePrefixNotSingleUppercase() {
        // Arrange
        CustomerPackageCategory newParent = new CustomerPackageCategory();
        newParent.setCategoryName("新套餐");
        newParent.setCategoryCode("PACKAGE_C");
        newParent.setLevel(1);
        newParent.setCodePrefix("AB"); // 多个字符

        // Act & Assert
        BadRequestException exception = assertThrows(BadRequestException.class,
            () -> service.create(newParent));

        assertEquals("编号前缀必须为单个大写英文字母", exception.getMessage());
    }
}