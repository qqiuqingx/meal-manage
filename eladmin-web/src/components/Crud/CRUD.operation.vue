<template>
  <div class="crud-opts">
    <span class="crud-opts-left">
      <!--左侧插槽-->
      <slot name="left" />
      <el-button
        v-if="crud.optShow.add"
        v-permission="permission.add"
        class="filter-item"
        size="mini"
        type="primary"
        icon="el-icon-plus"
        @click="crud.toAdd"
      >
        新增
      </el-button>
      <el-button
        v-if="crud.optShow.edit"
        v-permission="permission.edit"
        class="filter-item"
        size="mini"
        type="success"
        icon="el-icon-edit"
        :disabled="crud.selections.length !== 1"
        @click="crud.toEdit(crud.selections[0])"
      >
        修改
      </el-button>
      <el-button
        v-if="crud.optShow.del"
        slot="reference"
        v-permission="permission.del"
        class="filter-item"
        type="danger"
        icon="el-icon-delete"
        size="mini"
        :loading="crud.delAllLoading"
        :disabled="crud.selections.length === 0"
        @click="toDelete(crud.selections)"
      >
        删除
      </el-button>
      <el-button
        v-if="crud.optShow.download"
        :loading="crud.downloadLoading"
        :disabled="!crud.data.length"
        class="filter-item"
        size="mini"
        type="warning"
        icon="el-icon-download"
        @click="crud.doExport"
      >导出</el-button>
      <!--右侧-->
      <slot name="right" />
    </span>
    <el-button-group class="crud-opts-right">
      <el-button
        size="mini"
        plain
        type="info"
        icon="el-icon-search"
        @click="toggleSearch()"
      />
      <el-button
        size="mini"
        icon="el-icon-refresh"
        @click="crud.refresh()"
      />
      <el-popover
        placement="bottom-end"
        width="170"
        popper-class="crud-column-popover"
        trigger="click"
      >
        <el-button
          slot="reference"
          size="mini"
          icon="el-icon-s-grid"
        >
          <i
            class="fa fa-caret-down"
            aria-hidden="true"
          />
        </el-button>
        <el-checkbox
          v-model="allColumnsSelected"
          :indeterminate="allColumnsSelectedIndeterminate"
          @change="handleCheckAllChange"
        >
          全选
        </el-checkbox>
        <el-checkbox
          v-for="item in tableColumns"
          :key="item.key"
          v-model="item.visible"
          @change="handleCheckedTableColumnsChange(item)"
        >
          {{ item.label }}
        </el-checkbox>
      </el-popover>
    </el-button-group>
  </div>
</template>
<script>
import CRUD, { crud } from '@crud/crud'

function sortWithRef(src, ref) {
  const result = Object.assign([], ref)
  let cursor = -1
  src.forEach(e => {
    const idx = result.indexOf(e)
    if (idx === -1) {
      cursor += 1
      result.splice(cursor, 0, e)
    } else {
      cursor = idx
    }
  })
  return result
}

/** 返回表格列的稳定标识，优先使用模板列显式配置的 column-key。
 * @param {Object} config Element UI 列配置
 * @returns {string|undefined} 列标识
 */
function getColumnKey(config) {
  return config && (config.columnKey || config.property)
}

export default {
  mixins: [crud()],
  props: {
    permission: {
      type: Object,
      default: () => { return {} }
    },
    hiddenColumns: {
      type: Array,
      default: () => { return [] }
    },
    ignoreColumns: {
      type: Array,
      default: () => { return [] }
    },
    columnOrder: {
      type: Array,
      default: () => { return [] }
    }
  },
  data() {
    return {
      tableColumns: [],
      allColumnsSelected: true,
      allColumnsSelectedIndeterminate: false,
      tableUnwatcher: null,
      // 忽略下次表格列变动
      ignoreNextTableColumnsChange: false
    }
  },
  watch: {
    'crud.props.table'() {
      this.updateTableColumns()
      this.tableColumns.forEach(column => {
        if (this.hiddenColumns.indexOf(column.key) !== -1 || this.hiddenColumns.indexOf(column.property) !== -1) {
          column.visible = false
          this.updateColumnVisible(column)
        }
      })
    },
    'crud.props.table.store.states.columns'() {
      this.updateTableColumns()
    },
    columnOrder() {
      this.updateTableColumns()
    }
  },
  created() {
    this.crud.updateProp('searchToggle', true)
  },
  methods: {
    /** 根据表格当前列和指定顺序更新列设置列表，保留各列的显隐状态。 */
    updateTableColumns() {
      const table = this.crud.getTable()
      if (!table) {
        this.tableColumns = []
        return
      }
      let cols = null
      const columnFilter = e => e && e.type === 'default' && getColumnKey(e) && this.ignoreColumns.indexOf(getColumnKey(e)) === -1
      const refCols = table.columns.filter(columnFilter)
      if (this.ignoreNextTableColumnsChange) {
        this.ignoreNextTableColumnsChange = false
        return
      }
      this.ignoreNextTableColumnsChange = false
      const columns = []
      const fullTableColumns = table.$children.map(e => e.columnConfig).filter(columnFilter)
      cols = sortWithRef(fullTableColumns, refCols)
      if (this.columnOrder.length) {
        const orderIndex = new Map(this.columnOrder.map((key, index) => [key, index]))
        const defaultIndex = new Map(cols.map((config, index) => [getColumnKey(config), index]))
        cols.sort((a, b) => {
          const aKey = getColumnKey(a)
          const bKey = getColumnKey(b)
          const aIndex = orderIndex.has(aKey) ? orderIndex.get(aKey) : Number.MAX_SAFE_INTEGER
          const bIndex = orderIndex.has(bKey) ? orderIndex.get(bKey) : Number.MAX_SAFE_INTEGER
          return aIndex - bIndex || defaultIndex.get(aKey) - defaultIndex.get(bKey)
        })
      }
      cols.forEach(config => {
        const column = {
          key: getColumnKey(config),
          property: config.property,
          label: config.label,
          visible: refCols.indexOf(config) !== -1
        }
        columns.push(column)
      })
      this.tableColumns = columns
    },
    toDelete(datas) {
      this.$confirm(`确认删除选中的${datas.length}条数据?`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.crud.delAllLoading = true
        this.crud.doDelete(datas)
      }).catch(() => {
      })
    },
    handleCheckAllChange(val) {
      if (val === false) {
        this.allColumnsSelected = true
        return
      }
      this.tableColumns.forEach(column => {
        if (!column.visible) {
          column.visible = true
          this.updateColumnVisible(column)
        }
      })
      this.allColumnsSelected = val
      this.allColumnsSelectedIndeterminate = false
    },
    handleCheckedTableColumnsChange(item) {
      let totalCount = 0
      let selectedCount = 0
      this.tableColumns.forEach(column => {
        ++totalCount
        selectedCount += column.visible ? 1 : 0
      })
      if (selectedCount === 0) {
        this.crud.notify('请至少选择一列', CRUD.NOTIFICATION_TYPE.WARNING)
        this.$nextTick(function() {
          item.visible = true
        })
        return
      }
      this.allColumnsSelected = selectedCount === totalCount
      this.allColumnsSelectedIndeterminate = selectedCount !== totalCount && selectedCount !== 0
      this.updateColumnVisible(item)
    },
    /** 按列设置列表中的相邻可见列定位插入位置，并通知页面列显隐已变化。
     * @param {Object} item 要显示或隐藏的列设置
     */
    updateColumnVisible(item) {
      const table = this.crud.props.table
      const vm = table.$children.find(e => getColumnKey(e.columnConfig) === item.key)
      if (!vm) return
      const columnConfig = vm.columnConfig
      if (item.visible) {
        const activeColumns = table.store.states._columns
        const itemIndex = this.tableColumns.indexOf(item)
        const activeIndex = column => activeColumns.findIndex(config => getColumnKey(config) === column.key)
        const next = this.tableColumns.slice(itemIndex + 1).find(column => column.visible && activeIndex(column) !== -1)
        const previous = this.tableColumns.slice(0, itemIndex).reverse().find(column => column.visible && activeIndex(column) !== -1)
        const insertIndex = next ? activeIndex(next) : (previous ? activeIndex(previous) + 1 : activeColumns.length)
        vm.owner.store.commit('insertColumn', columnConfig, insertIndex, null)
      } else {
        vm.owner.store.commit('removeColumn', columnConfig, null)
      }
      this.ignoreNextTableColumnsChange = true
      this.$emit('column-visibility-change', item)
    },
    toggleSearch() {
      this.crud.props.searchToggle = !this.crud.props.searchToggle
    }
  }
}
</script>

<style>
  .crud-opts {
    padding: 4px 0;
    display: -webkit-flex;
    display: flex;
    align-items: center;
  }
  .crud-opts .crud-opts-right {
    margin-left: auto;
  }
  .crud-opts .crud-opts-right span {
    float: left;
  }
  .crud-column-popover {
    max-height: 60vh;
    overflow-y: auto;
  }
</style>
