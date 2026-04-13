<template>
  <div class="app-container editorial-app relative min-h-screen">
    <!-- Header -->
    <header class="flex-col md-flex-row flex-between mb-10 gap-4 w-full">
      <div class="header-left">
        <div class="week-selector-wrap">
          <select v-model="currentWeek" class="week-selector uppercase tracking-wider font-bold">
            <option value="1">第一周</option>
            <option value="2">第二周</option>
            <option value="3">第三周</option>
            <option value="4">第四周</option>
          </select>
          <span class="el-icon-arrow-down selector-icon" />
        </div>
        <p class="text-primary font-bold tracking-widest text-xs uppercase mb-1 mt-2">菜单视图全景</p>
        <h1 class="editorial-title tracking-tight text-4xl">每周菜单</h1>
      </div>
      <div class="header-actions">
        <div class="tab-switcher shadow-inner">
          <button class="tab-btn active">当前周</button>
          <button class="tab-btn inactive" @click="handlePrint">下一周</button>
        </div>
        <!-- 行显示控制 -->
        <el-popover placement="bottom-end" trigger="click" width="260" popper-class="row-filter-popover">
          <div class="row-filter-panel">
            <p class="row-filter-title">控制显示行</p>
            <div class="row-filter-tags">
              <span
                v-for="type in dishTypes"
                :key="type.key"
                class="row-tag"
                :class="{ 'row-tag-active': !type.hidden, 'row-tag-hidden': type.hidden }"
                @click="type.hidden = !type.hidden"
              >
                <i :class="type.hidden ? 'el-icon-minus' : 'el-icon-check'" class="row-tag-icon" />
                {{ type.cn }}
              </span>
            </div>
            <div class="row-filter-footer">
              <span class="row-filter-hint">点击 tag 切换行的显示/隐藏</span>
            </div>
          </div>
          <button slot="reference" class="btn-outlined flex items-center gap-2">
            <i class="el-icon-menu" /> 显示行
            <span v-if="dishTypes.some(t => t.hidden)" class="row-filter-badge">{{ dishTypes.filter(t => t.hidden).length }}</span>
          </button>
        </el-popover>
        <button class="btn-outlined flex items-center gap-2" @click="handlePrint">
          <i class="el-icon-printer" /> 打印视图
        </button>
      </div>
    </header>

    <!-- Matrix Canvas -->
    <div class="matrix-canvas-wrapper shadow-2xl rounded-2xl p-0.5 bg-surface-container-low overflow-hidden">
      <div class="matrix-canvas bg-surface-container-lowest rounded-xl overflow-x-auto min-w-[900px]">

        <!-- Table Headers -->
        <div class="meal-grid header-row">
          <div class="p-5 border-b border-surface-container-high col-header" />
          <div v-for="(dayName, idx) in days" :key="idx" class="p-5 text-center col-header border-l">
            <p class="day-en tracking-tighter">{{ dayName.en }}</p>
            <p class="day-cn font-headline font-extrabold">{{ dayName.cn }}</p>
          </div>
        </div>

        <!-- Lunch Section -->
        <div class="meal-section relative">
          <div class="meal-indicator lunch-indicator">
            <span class="rotated-text text-primary font-extrabold">午餐</span>
          </div>

          <div v-for="type in visibleDishTypes" :key="'lunch-' + type.key" class="meal-grid border-b">
            <div class="row-header">
              <span class="text-xs font-bold text-slate-400">{{ type.cn }} / {{ type.en }}</span>
            </div>
            <div
              v-for="dayIdx in 7"
              :key="'lunch-cell-' + type.key + '-' + dayIdx"
              class="matrix-cell border-l group-cell"
              @click.self="handleCellClick('LUNCH', type.key, dayIdx)"
            >
              <div class="dish-stack">
                <div
                  v-for="slot in matrix['LUNCH'][type.key][dayIdx - 1]"
                  :key="slot.id"
                  class="dish-card lunch-dish group"
                  @click="handleUpdate(slot.dish)"
                >
                  <p class="dish-name">{{ slot.dish ? slot.dish.name : ('菜品' + slot.dishId) }}</p>
                  <i class="el-icon-circle-close remove-icon text-red-500 absolute top-1 right-1 opacity-0 group-hover-opacity-100 transition-opacity" title="移出该日排期" @click.stop="handleRemoveSlot(slot)" />
                </div>
              </div>

              <div v-if="matrix['LUNCH'][type.key][dayIdx - 1].length === 0" class="flex items-center justify-center gap-1">
                <button
                  class="add-btn group-icon"
                  title="直接新建菜品"
                  @click.prevent="handleCellClick('LUNCH', type.key, dayIdx)"
                >
                  <i class="el-icon-circle-plus" />
                  <span class="add-text">新建</span>
                </button>
                <button
                  class="add-btn group-icon"
                  title="从库中选择"
                  @click.prevent="openSelector('LUNCH', type.key, dayIdx)"
                >
                  <i class="el-icon-search" />
                  <span class="add-text">选择</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Dinner Section -->
        <div class="meal-section relative">
          <div class="meal-indicator dinner-indicator">
            <span class="rotated-text text-tertiary font-extrabold">晚餐</span>
          </div>

          <div v-for="type in visibleDishTypes" :key="'dinner-' + type.key" class="meal-grid border-b">
            <div class="row-header">
              <span class="text-xs font-bold text-slate-400">{{ type.cn }} / {{ type.en }}</span>
            </div>
            <div
              v-for="dayIdx in 7"
              :key="'dinner-cell-' + type.key + '-' + dayIdx"
              class="matrix-cell border-l group-cell"
              @click.self="handleCellClick('DINNER', type.key, dayIdx)"
            >
              <div class="dish-stack">
                <div
                  v-for="slot in matrix['DINNER'][type.key][dayIdx - 1]"
                  :key="slot.id"
                  class="dish-card dinner-dish group"
                  @click="handleUpdate(slot.dish)"
                >
                  <p class="dish-name">{{ slot.dish ? slot.dish.name : ('菜品' + slot.dishId) }}</p>
                  <i class="el-icon-circle-close remove-icon text-red-500 absolute top-1 right-1 opacity-0 group-hover-opacity-100 transition-opacity" title="移出该日排期" @click.stop="handleRemoveSlot(slot)" />
                </div>
              </div>

              <div v-if="matrix['DINNER'][type.key][dayIdx - 1].length === 0" class="flex items-center justify-center gap-1">
                <button
                  class="add-btn group-icon"
                  title="直接新建菜品"
                  @click.prevent="handleCellClick('DINNER', type.key, dayIdx)"
                >
                  <i class="el-icon-circle-plus" />
                  <span class="add-text">新建</span>
                </button>
                <button
                  class="add-btn group-icon"
                  title="从库中选择"
                  @click.prevent="openSelector('DINNER', type.key, dayIdx)"
                >
                  <i class="el-icon-search" />
                  <span class="add-text">选择</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Global Floating Add Button -->
    <button class="fab-btn editorial-gradient shadow-2xl transition-transform transform active:scale-95 hover:scale-110 no-print" @click="handleAddGlobal">
      <i class="el-icon-plus text-white font-bold text-2xl leading-none" />
    </button>

    <!-- 新增/编辑弹窗 -->
    <dish-form ref="dishForm" @refresh="getList" @saved="handleDishFormSaved" />

    <!-- ═══════════════════════════════════════════════
         打印预览区 — 屏幕上隐藏，打印时完整显示
    ═══════════════════════════════════════════════ -->
    <div class="print-sheet">
      <!-- 打印页头 -->
      <div class="ps-header">
        <div class="ps-header__brand">
          <span class="ps-label">每周菜单</span>
          <span class="ps-week">第{{ currentWeek }}周</span>
        </div>
        <div class="ps-header__meta">
          <span>打印时间：{{ printTime }}</span>
        </div>
      </div>

      <!-- 午餐打印表格 -->
      <div class="ps-section">
        <div class="ps-section-title">🍜 午餐</div>
        <table class="ps-table">
          <thead>
            <tr>
              <th class="ps-th-type">类别</th>
              <th v-for="(d, i) in days" :key="'ph-l-' + i" class="ps-th-day">{{ d.cn }}<br><span class="ps-day-en">{{ d.en }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="type in visibleDishTypes" :key="'prl-' + type.key">
              <td class="ps-td-type">
                <span :class="'ps-type-tag ps-type-' + type.key.toLowerCase()">{{ type.cn }}</span>
              </td>
              <td v-for="dayIdx in 7" :key="'plc-' + type.key + '-' + dayIdx" class="ps-td-cell">
                <div v-for="slot in matrix['LUNCH'][type.key][dayIdx - 1]" :key="slot.id" class="ps-dish-name">
                  {{ slot.dish ? slot.dish.name : ('菜品' + slot.dishId) }}
                </div>
                <span v-if="matrix['LUNCH'][type.key][dayIdx - 1].length === 0" class="ps-empty">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 晚餐打印表格 -->
      <div class="ps-section">
        <div class="ps-section-title">🍲 晚餐</div>
        <table class="ps-table">
          <thead>
            <tr>
              <th class="ps-th-type">类别</th>
              <th v-for="(d, i) in days" :key="'ph-d-' + i" class="ps-th-day">{{ d.cn }}<br><span class="ps-day-en">{{ d.en }}</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="type in visibleDishTypes" :key="'prd-' + type.key">
              <td class="ps-td-type">
                <span :class="'ps-type-tag ps-type-' + type.key.toLowerCase()">{{ type.cn }}</span>
              </td>
              <td v-for="dayIdx in 7" :key="'pdc-' + type.key + '-' + dayIdx" class="ps-td-cell">
                <div v-for="slot in matrix['DINNER'][type.key][dayIdx - 1]" :key="slot.id" class="ps-dish-name">
                  {{ slot.dish ? slot.dish.name : ('菜品' + slot.dishId) }}
                </div>
                <span v-if="matrix['DINNER'][type.key][dayIdx - 1].length === 0" class="ps-empty">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="ps-footer">
        <span>© 菜单管理系统 · 第{{ currentWeek }}周菜单</span>
        <span>本表格由系统自动生成，仅显示当前可见行</span>
      </div>
    </div>

    <!-- 落位选择弹窗 -->
    <el-dialog :visible.sync="selectorVisible" title="选择菜品入库" width="500px" append-to-body>
      <div class="p-4">
        <p class="mb-4 text-sm text-on-surface-variant">为 <strong>本周{{ selectorQuery.day }}</strong> 的 <strong>{{ selectorQuery.mealType === 'LUNCH' ? '午餐' : '晚餐' }}</strong> 挑选菜品：</p>
        <el-select
          v-model="selectedDishId"
          filterable
          remote
          reserve-keyword
          placeholder="请输入菜品名称搜索..."
          :remote-method="searchDishes"
          :loading="selectorLoading"
          class="w-full mb-6"
          size="large"
        >
          <el-option
            v-for="item in selectorOptions"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          >
            <div class="flex items-center gap-3">
              <img v-if="item.imageUrl" :src="item.imageUrl" class="w-8 h-8 rounded object-cover">
              <span>{{ item.name }}</span>
            </div>
          </el-option>
        </el-select>
        <div class="flex justify-end gap-3 mt-4">
          <el-button @click="selectorVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" class="editorial-gradient text-white border-none" @click="confirmAddSlot">确认落位</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { queryDishes } from '@/api/dish'
import { queryMealSchedule, addMealSchedule, deleteMealSchedule } from '@/api/mealSchedule'
import DishForm from './dish'

export default {
  name: 'DishMatrix',
  components: {
    DishForm
  },
  data() {
    return {
      loading: true,
      currentWeek: '1',
      slots: [],
      selectorVisible: false,
      selectorLoading: false,
      selectedDishId: null,
      selectorOptions: [],
      submitting: false,
      selectorQuery: {
        weekNum: 1,
        day: 1,
        mealType: '',
        dishCategory: ''
      },
      pendingScheduleSlot: null,
      days: [
        { en: 'Mon', cn: '星期一' },
        { en: 'Tue', cn: '星期二' },
        { en: 'Wed', cn: '星期三' },
        { en: 'Thu', cn: '星期四' },
        { en: 'Fri', cn: '星期五' },
        { en: 'Sat', cn: '星期六' },
        { en: 'Sun', cn: '星期日' }
      ],
      dishTypes: [
        { key: 'SOUP', cn: '汤', en: 'Soup', hidden: false },
        { key: 'MAIN', cn: '主菜', en: 'Main', hidden: false },
        { key: 'SIDE', cn: '副菜', en: 'Side', hidden: false },
        { key: 'VEGETABLE', cn: '素菜', en: 'Veg', hidden: false },
        { key: 'RICE', cn: '米饭', en: 'Rice', hidden: false }
      ]
    }
  },
  computed: {
    visibleDishTypes() {
      return this.dishTypes.filter(t => !t.hidden)
    },
    printTime() {
      const now = new Date()
      const fmt = n => String(n).padStart(2, '0')
      return `${now.getFullYear()}-${fmt(now.getMonth() + 1)}-${fmt(now.getDate())} ${fmt(now.getHours())}:${fmt(now.getMinutes())}`
    },
    matrix() {
      const layout = {
        LUNCH: { SOUP: [], MAIN: [], SIDE: [], VEGETABLE: [], RICE: [] },
        DINNER: { SOUP: [], MAIN: [], SIDE: [], VEGETABLE: [], RICE: [] }
      }
      for (const meal of ['LUNCH', 'DINNER']) {
        for (const type of ['SOUP', 'MAIN', 'SIDE', 'VEGETABLE', 'RICE']) {
          layout[meal][type] = [[], [], [], [], [], [], []]
        }
      }

      this.slots.forEach(slot => {
        const dayIdx = slot.dayOfWeek - 1 // 0 to 6
        if (dayIdx >= 0 && dayIdx <= 6 && layout[slot.mealTime] && layout[slot.mealTime][slot.dishCategory]) {
          layout[slot.mealTime][slot.dishCategory][dayIdx].push(slot)
        }
      })
      return layout
    }
  },
  watch: {
    currentWeek() {
      this.getList()
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      queryMealSchedule({ weekNum: parseInt(this.currentWeek) }).then(response => {
        this.slots = response.slots || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleRemoveSlot(slot) {
      const dishName = slot.dish ? slot.dish.name : '该菜品'
      this.$confirm(`确定要从排餐坑位中移出【${dishName}】吗？`, '移除提示', {
        confirmButtonText: '移出',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteMealSchedule(slot.id).then(() => {
          this.$message.success('已移出排餐坑位')
          this.getList()
        })
      })
    },
    handleUpdate(dish) {
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleUpdate(dish)
      }
    },
    handleCellClick(mealType, dishType, day) {
      this.pendingScheduleSlot = {
        weekNum: parseInt(this.currentWeek),
        day: day,
        mealType: mealType,
        dishCategory: dishType
      }
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleAdd()
      }
    },
    handleDishFormSaved(form) {
      // 捕获菜品保存成功事件。如果是通过格子"新建"打开的，则自动发起落位绑定。
      if (this.pendingScheduleSlot && !form.id) {
        queryDishes({ name: form.name, page: 0, size: 1 }).then(res => {
          if (res.content && res.content.length > 0) {
            const newDishId = res.content[0].id
            const payload = {
              weekNum: this.pendingScheduleSlot.weekNum,
              dayOfWeek: this.pendingScheduleSlot.day,
              mealTime: this.pendingScheduleSlot.mealType,
              dishCategory: this.pendingScheduleSlot.dishCategory,
              dishId: newDishId
            }
            addMealSchedule(payload).then(() => {
              this.pendingScheduleSlot = null
              this.getList()
            })
          }
        }).catch(() => { this.pendingScheduleSlot = null })
      } else {
        this.pendingScheduleSlot = null
      }
    },
    openSelector(mealType, dishType, day) {
      this.selectorQuery = {
        weekNum: parseInt(this.currentWeek),
        day: day,
        mealType: mealType,
        dishCategory: dishType
      }
      this.selectedDishId = null
      this.selectorOptions = []
      this.selectorVisible = true
      this.searchDishes('')
    },
    searchDishes(query) {
      this.selectorLoading = true
      queryDishes({ name: query, page: 0, size: 50, enabled: true }).then(res => {
        this.selectorOptions = res.content || []
        this.selectorLoading = false
      }).catch(() => {
        this.selectorLoading = false
      })
    },
    confirmAddSlot() {
      if (!this.selectedDishId) {
        this.$message.warning('请先选择一道菜品')
        return
      }
      this.submitting = true
      const payload = {
        weekNum: this.selectorQuery.weekNum,
        dayOfWeek: this.selectorQuery.day,
        mealTime: this.selectorQuery.mealType,
        dishCategory: this.selectorQuery.dishCategory,
        dishId: this.selectedDishId
      }
      addMealSchedule(payload).then(() => {
        this.$message.success('落位成功')
        this.selectorVisible = false
        this.submitting = false
        this.getList()
      }).catch(err => {
        this.submitting = false
        console.error(err)
      })
    },
    handleAddGlobal() {
      this.pendingScheduleSlot = null
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleAdd()
      }
    },
    handlePrint() {
      window.print()
    }
  }
}
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;700;800&family=Inter:wght@400;500;600&display=swap');

/* Color Variables injected from prior theme setup */
.editorial-app {
  --primary: #006b5c;
  --primary-container: #00bfa5;
  --tertiary: #ac3509;
  --surface: #f8f9fb;
  --surface-container-lowest: #ffffff;
  --surface-container-low: #f2f4f6;
  --surface-container: #eceef0;
  --surface-container-high: #e6e8ea;
  --surface-container-highest: #e0e3e5;
  --on-surface: #191c1e;
  --on-surface-variant: #3c4a46;
  --outline-variant: #bbcac4;

  font-family: 'Inter', sans-serif;
  background-color: var(--surface);
  color: var(--on-surface);
  padding: 32px 40px;
}

/* Utilities */
.flex-between { display: flex; justify-content: space-between; align-items: flex-end; }
.flex { display: flex; }
.items-center { align-items: center; }
.gap-2 { gap: 8px; }
.gap-4 { gap: 16px; }
.mt-2 { margin-top: 8px; }
.mb-1 { margin-bottom: 4px; }
.mb-10 { margin-bottom: 40px; }
.mb-4 { margin-bottom: 16px; }
.uppercase { text-transform: uppercase; }
.font-bold { font-weight: 700; }
.font-extrabold { font-weight: 800; }
.text-4xl { font-size: 2.25rem; font-family: 'Manrope', sans-serif;}
.text-lg { font-size: 1.125rem; }
.text-sm { font-size: 0.875rem; }
.text-xs { font-size: 0.75rem; }
.tracking-tight { letter-spacing: -0.025em; }
.tracking-tighter { letter-spacing: -0.05em; }
.tracking-widest { letter-spacing: 0.1em; }
.tracking-wider { letter-spacing: 0.05em; }

.text-primary { color: var(--primary); }
.text-tertiary { color: var(--tertiary); }
.text-slate-400 { color: #94a3b8; }
.text-slate-500 { color: #64748b; }
.bg-surface-container-low { background-color: var(--surface-container-low); }
.bg-surface-container-lowest { background-color: var(--surface-container-lowest); }
.shadow-2xl { box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.05); }
.shadow-sm { box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.05); }
.shadow-inner { box-shadow: inset 0 2px 4px 0 rgba(0, 0, 0, 0.03); }
.shadow-xl { box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.05), 0 10px 10px -5px rgba(0, 0, 0, 0.04); }
.rounded-2xl { border-radius: 1rem; }
.rounded-xl { border-radius: 0.75rem; }
.rounded-full { border-radius: 9999px; }
.p-0\.5 { padding: 2px; }
.inline-block { display: inline-block; }
.relative { position: relative; }
.absolute { position: absolute; }

/* Header Utilities */
.week-selector-wrap {
  position: relative;
  width: 12rem;
}
.week-selector {
  appearance: none;
  width: 100%;
  background-color: var(--surface-container-low);
  border: 1px solid rgba(187, 202, 196, 0.2);
  color: var(--on-surface-variant);
  font-size: 0.75rem;
  padding: 8px 16px;
  border-radius: 0.5rem;
  cursor: pointer;
  outline: none;
  transition: all 0.2s;
}
.week-selector:focus {
  box-shadow: 0 0 0 2px rgba(0, 191, 165, 0.2);
}
.selector-icon {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: #64748b;
  pointer-events: none;
  font-size: 0.75rem;
}

.tab-switcher {
  display: flex;
  background-color: var(--surface-container-low);
  padding: 4px;
  border-radius: 9999px;
}
.tab-btn {
  background: transparent;
  border: none;
  padding: 6px 16px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-btn.active {
  background-color: var(--surface-container-lowest);
  color: var(--primary);
  box-shadow: 0 1px 2px 0 rgba(0,0,0,0.05);
}
.tab-btn.inactive {
  color: #94a3b8;
  font-weight: 500;
}
.tab-btn.inactive:hover { color: #64748b; }

.btn-outlined {
  background-color: var(--surface-container-high);
  color: var(--on-surface);
  padding: 8px 20px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  border: 1px solid rgba(187, 202, 196, 0.2);
  cursor: pointer;
  transition: all 0.2s;
}
.btn-outlined:hover { background-color: var(--surface-container-highest); }

.editorial-gradient {
  background: linear-gradient(135deg, #006b5c 0%, #00bfa5 100%);
}

/* CSS Grid layout natively mapping standard grid sizes */
.meal-grid {
  display: grid;
  grid-template-columns: 100px repeat(7, minmax(130px, 1fr));
  align-items: stretch;
}
.border-b { border-bottom: 1px solid rgba(230, 232, 234, 0.6); }
.border-l { border-left: 1px solid rgba(230, 232, 234, 0.6); }

.header-row {
  background-color: rgba(242, 244, 246, 0.3);
}
.day-en { font-size: 0.75rem; font-weight: 700; color: #94a3b8; text-transform: uppercase; margin-bottom: 4px; }
.day-cn { color: #1e293b; }

/* The Row Indicator overlay */
.meal-indicator {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}
.meal-indicator.border-r { border-right: 1px solid rgba(230, 232, 234, 0.8); }
.lunch-indicator { background-color: rgba(0, 191, 165, 0.05); }
.dinner-indicator { background-color: rgba(172, 53, 9, 0.05); }

.rotated-text {
  writing-mode: vertical-rl;
  text-orientation: upright;
  letter-spacing: 0.4em;
  white-space: nowrap;
}

.row-header {
  margin-left: 48px;
  padding: 16px;
  display: flex;
  align-items: center;
}

/* Matrix Cells */
.matrix-cell {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: background-color 0.2s;
  height: 100%;
}
.group-cell:hover {
  background-color: rgba(248, 249, 251, 0.4);
}
.dish-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.dish-card {
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.dish-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05);
}
.dish-name {
  font-size: 0.875rem;
  font-weight: 600;
  margin: 0;
  line-height: 1.3;
}
.edit-icon {
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.2s;
}
.dish-card:hover .edit-icon { opacity: 1; }
.remove-icon {
  opacity: 0;
  background: white;
  border-radius: 50%;
  padding: 2px;
}
.dish-card:hover .remove-icon { opacity: 1; }

.lunch-dish {
  background-color: rgba(0, 191, 165, 0.1);
  border: 1px solid rgba(0, 191, 165, 0.2);
  color: var(--primary);
}
.dinner-dish {
  background-color: rgba(172, 53, 9, 0.08); /* tertiary fixed */
  border: 1px solid rgba(172, 53, 9, 0.15);
  color: var(--tertiary);
}

.add-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #cbd5e1;
  padding: 12px 0;
  transition: all 0.2s;
  margin: auto; /* align center if alone */
}
/* hide ADD by default */
.add-text { font-size: 10px; font-weight: 800; opacity: 0; transition: opacity 0.2s; letter-spacing: 0.05em; }
.group-cell:hover .add-btn { color: var(--primary); }
.group-cell:hover .add-btn .add-text { opacity: 1; }

/* Secondary Insights Panels */
.insight-cards-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}
@media (max-width: 1024px) {
  .insight-cards-grid { grid-template-columns: 1fr; }
}

.border { border: 1px solid rgba(187, 202, 196, 0.2); }
.insight-card {
  background-color: var(--surface-container-lowest);
  padding: 24px;
  border-radius: 1rem;
}
.icon-circle {
  width: 40px; height: 40px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
}
.icon-circle-primary { background-color: rgba(0, 107, 92, 0.1); }
.icon-circle-tertiary { background-color: rgba(172, 53, 9, 0.1); }

.inventory-pill {
  color: var(--tertiary);
  background-color: rgba(172, 53, 9, 0.1);
}

.w-\[78\%\] { width: 78%; }

/* Floating Action Button Global */
.fab-btn {
  position: fixed;
  bottom: 2rem;
  right: 2rem;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
}

/* ─── 打印区（屏幕隐藏、打印显示） ─── */
.print-sheet {
  display: none;
}

/* Row filter popover */
.row-filter-panel {
  padding: 4px 0;
}
.row-filter-title {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 12px;
}
.row-filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.row-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  border: 1.5px solid transparent;
}
.row-tag-active {
  background-color: rgba(0, 107, 92, 0.1);
  color: var(--primary);
  border-color: rgba(0, 107, 92, 0.25);
}
.row-tag-active:hover {
  background-color: rgba(0, 107, 92, 0.18);
}
.row-tag-hidden {
  background-color: #f1f5f9;
  color: #94a3b8;
  border-color: rgba(148, 163, 184, 0.2);
  text-decoration: line-through;
}
.row-tag-hidden:hover {
  background-color: #e2e8f0;
  color: #64748b;
}
.row-tag-icon {
  font-size: 11px;
}
.row-filter-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid rgba(230, 232, 234, 0.8);
}
.row-filter-hint {
  font-size: 0.7rem;
  color: #94a3b8;
}
.row-filter-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background-color: var(--tertiary);
  color: white;
  font-size: 10px;
  font-weight: 800;
  margin-left: 2px;
}

/* ─── @media print ─── */
@media print {
  /* 隐藏所有交互元素 */
  .no-print,
  header,
  .matrix-canvas-wrapper,
  .fab-btn { display: none !important; }

  /* 显示打印区 */
  .print-sheet { display: block !important; }

  .editorial-app {
    padding: 0 !important;
    background: #fff !important;
  }
}
</style>

<!-- 全局打印：隐藏 eladmin 框架导航 -->
<style>
@media print {
  .navbar, .app-header-wrapper, .el-header { display: none !important; }
  .tags-view-container, .tagsView-container, .tags-view-wrapper { display: none !important; }
  .sidebar-container, .side-bar, .el-aside { display: none !important; }
  .footer, .el-footer, .app-footer { display: none !important; }
  .main-container, .app-main, .el-main {
    margin-left: 0 !important;
    padding: 0 !important;
    width: 100% !important;
  }
  .app-wrapper, #app {
    padding: 0 !important;
    margin: 0 !important;
    width: 100% !important;
  }

  /* 打印表格样式 */
  .print-sheet {
    font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
    padding: 16px 20px;
    color: #1e293b;
  }
  .ps-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    border-bottom: 2px solid #006b5c;
    padding-bottom: 10px;
    margin-bottom: 20px;
  }
  .ps-header__brand { display: flex; align-items: baseline; gap: 12px; }
  .ps-label {
    font-size: 22px;
    font-weight: 900;
    color: #006b5c;
    letter-spacing: -0.5px;
  }
  .ps-week {
    font-size: 14px;
    font-weight: 700;
    color: #64748b;
    background: #f1f5f9;
    padding: 2px 10px;
    border-radius: 4px;
  }
  .ps-header__meta {
    font-size: 11px;
    color: #94a3b8;
  }
  .ps-section {
    margin-bottom: 24px;
    break-inside: avoid;
  }
  .ps-section-title {
    font-size: 13px;
    font-weight: 800;
    color: #475569;
    letter-spacing: 0.05em;
    margin-bottom: 6px;
    padding: 4px 0;
    border-bottom: 1px solid #e2e8f0;
  }
  .ps-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 11px;
  }
  .ps-th-type {
    width: 52px;
    text-align: center;
    font-weight: 900;
    font-size: 10px;
    letter-spacing: 0.08em;
    color: #64748b;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    padding: 6px 4px;
  }
  .ps-th-day {
    text-align: center;
    font-weight: 700;
    font-size: 11px;
    color: #334155;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    padding: 6px 4px;
    line-height: 1.3;
  }
  .ps-day-en {
    font-size: 9px;
    color: #94a3b8;
    font-weight: 500;
  }
  .ps-td-type {
    text-align: center;
    border: 1px solid #e2e8f0;
    padding: 6px 4px;
    background: #fafafa;
  }
  .ps-type-tag {
    display: inline-block;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 10px;
    font-weight: 700;
  }
  .ps-type-soup      { background: #e0f2fe; color: #0369a1; }
  .ps-type-main      { background: #fee2e2; color: #991b1b; }
  .ps-type-side      { background: #fef3c7; color: #92400e; }
  .ps-type-vegetable { background: #dcfce7; color: #166534; }
  .ps-type-rice      { background: #f3f4f6; color: #374151; }
  .ps-td-cell {
    border: 1px solid #e2e8f0;
    padding: 6px 8px;
    vertical-align: top;
    min-height: 36px;
  }
  .ps-dish-name {
    font-weight: 600;
    color: #1e293b;
    line-height: 1.5;
    font-size: 11px;
  }
  .ps-empty {
    color: #cbd5e1;
    font-size: 10px;
  }
  .ps-footer {
    display: flex;
    justify-content: space-between;
    margin-top: 16px;
    padding-top: 8px;
    border-top: 1px solid #e2e8f0;
    font-size: 9px;
    color: #94a3b8;
  }
}
</style>

