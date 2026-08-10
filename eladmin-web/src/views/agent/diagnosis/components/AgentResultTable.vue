<template>
  <div class="agent-result-table">
    <div v-if="sections.length" class="table-sections">
      <div v-for="section in sections" :key="section.id" class="table-section">
        <div v-if="section.title" class="section-title">{{ section.title }}</div>
        <el-table v-if="section.rows.length" :data="pagedRows(section)" size="mini" border>
          <el-table-column
            v-for="column in section.columns"
            :key="column.field"
            :prop="column.field"
            :label="column.label"
            min-width="120"
          >
            <template slot-scope="scope">
              <span :class="cellClass(column.field)">{{ cellValue(scope.row, column) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="selectable" label="操作" width="80" fixed="right">
            <template slot-scope="scope">
              <el-button
                v-if="canSelectRow(scope.row)"
                type="primary"
                size="mini"
                plain
                @click="$emit('select', scope.row)"
              >
                选择
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无数据" :image-size="64" />
        <el-pagination
          v-if="hasPagination(section)"
          class="local-pagination"
          background
          layout="prev, pager, next"
          :current-page="pageFor(section.id)"
          :page-size="pageSizeNumber"
          :total="section.rows.length"
          @current-change="changePage(section.id, $event)"
        />
      </div>
    </div>
    <div v-else class="table-empty">暂无数据</div>
  </div>
</template>

<script>
import {
  formatFieldValue,
  isSafeLabel,
  isSafePath,
  isSupportedFormat,
  readPath
} from '../utils/agentPresentationFormatters'

const MAX_TABLE_COLUMNS = 20
const MAX_SECTIONS = 8

function normalizeColumns(columns) {
  if (!Array.isArray(columns)) {
    return []
  }
  return columns
    .filter(column => column && isSafePath(column.field) && isSafeLabel(column.label, 30) && isSupportedFormat(column.format))
    .slice(0, MAX_TABLE_COLUMNS)
}

function rowsAt(data, path) {
  if (!isSafePath(path)) {
    return []
  }
  const value = readPath(data, path)
  if (!Array.isArray(value)) {
    return []
  }
  return value.filter(row => row && Object.prototype.toString.call(row) === '[object Object]')
}

function createSection(data, descriptor, index, fallbackTitle) {
  const section = descriptor || {}
  return {
    id: section.id || `section-${index}`,
    title: isSafeLabel(section.title, 100) ? section.title : fallbackTitle,
    columns: normalizeColumns(section.columns),
    rows: rowsAt(data, section.dataPath)
  }
}

export default {
  name: 'AgentResultTable',
  props: {
    card: { type: Object, default: null },
    data: { type: [Object, Array], default: null },
    descriptor: { type: Object, default: null },
    pageSize: { type: Number, default: 10 },
    table: { type: Object, default: null },
    selectable: { type: Boolean, default: false }
  },
  data() {
    return {
      currentPages: {}
    }
  },
  computed: {
    sourceData() {
      if (this.data !== null && this.data !== undefined) {
        return this.data
      }
      return this.card && this.card.data ? this.card.data : {}
    },
    tableDescriptor() {
      return this.table || (this.descriptor && this.descriptor.table) || null
    },
    pageSizeNumber() {
      const value = Number(this.pageSize)
      return isFinite(value) && value > 0 ? Math.floor(value) : 10
    },
    sections() {
      const descriptor = this.tableDescriptor
      if (!descriptor || !isSafePath(descriptor.dataPath)) {
        return []
      }
      const configuredSections = Array.isArray(descriptor.sections) ? descriptor.sections.slice(0, MAX_SECTIONS) : []
      if (configuredSections.length) {
        return configuredSections.map((section, index) => createSection(this.sourceData, section, index, `分区 ${index + 1}`))
      }
      return [createSection(this.sourceData, descriptor, 0, '')]
    }
  },
  methods: {
    /** 返回当前分区的本地页码，数据缩减后自动回到最后一页。 */
    pageFor(sectionId) {
      let section = null
      for (let index = 0; index < this.sections.length; index++) {
        if (this.sections[index].id === sectionId) {
          section = this.sections[index]
          break
        }
      }
      const totalPages = section ? Math.max(1, Math.ceil(section.rows.length / this.pageSizeNumber)) : 1
      const current = Number(this.currentPages[sectionId]) || 1
      return Math.min(Math.max(current, 1), totalPages)
    },

    /** 返回指定分区当前页的已有数据，不向后端发起分页查询。 */
    pagedRows(section) {
      const page = this.pageFor(section.id)
      const start = (page - 1) * this.pageSizeNumber
      return section.rows.slice(start, start + this.pageSizeNumber)
    },

    /** 记录当前分区页码，确保每张结果卡片和每个分区互不影响。 */
    changePage(sectionId, page) {
      const value = Number(page) || 1
      if (this.$set) {
        this.$set(this.currentPages, sectionId, value)
      } else {
        this.currentPages[sectionId] = value
      }
    },

    /** 使用集中格式化器读取并显示受控字段。 */
    cellValue(row, column) {
      return formatFieldValue(row, column.field, column.format)
    },

    /** 为客户编号和姓名提供稳定的主标识/辅助信息样式。 */
    cellClass(field) {
      if (field === 'customerCode') return 'customer-code'
      if (field === 'customerName') return 'customer-name'
      return 'table-cell-text'
    },

    /** 仅允许带有安全客户编号的候选行触发选择，避免把内部 ID 暴露到后续查询。 */
    canSelectRow(row) {
      return !!(row && typeof row.customerCode === 'string' && row.customerCode.trim())
    },

    /** 判断当前分区是否需要显示本地分页控件。 */
    hasPagination(section) {
      return section.rows.length > this.pageSizeNumber
    }
  }
}
</script>

<style scoped>
.agent-result-table {
  min-width: 0;
}

.table-section + .table-section {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid #ebeef5;
}

.section-title {
  margin-bottom: 8px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}

.table-cell-text,
.customer-code,
.customer-name {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  line-height: 1.5;
  vertical-align: middle;
  white-space: pre-wrap;
  word-break: break-word;
}

.customer-code {
  color: #303133;
  font-weight: 600;
}

.customer-name {
  color: #606266;
}

.local-pagination {
  margin-top: 10px;
  text-align: right;
}

.table-empty {
  padding: 16px;
  color: #909399;
  text-align: center;
}
</style>
