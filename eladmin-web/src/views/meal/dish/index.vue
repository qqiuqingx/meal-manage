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
        <p class="text-primary font-bold tracking-widest text-xs uppercase mb-1 mt-2">排餐视图全景</p>
        <h1 class="editorial-title tracking-tight text-4xl">每周排餐计划</h1>
      </div>
      <div class="header-actions">
        <div class="tab-switcher shadow-inner">
          <button class="tab-btn active">当前周</button>
          <button class="tab-btn inactive" @click="handlePrint">下一周</button>
        </div>
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

          <div v-for="type in dishTypes" :key="'lunch-' + type.key" class="meal-grid border-b">
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
                  v-for="dish in matrix['LUNCH'][type.key][dayIdx - 1]"
                  :key="dish.id"
                  class="dish-card lunch-dish group"
                  @click="handleUpdate(dish)"
                >
                  <p class="dish-name">{{ dish.name }}</p>
                  <i class="el-icon-edit edit-icon" />
                </div>
              </div>

              <button
                v-if="matrix['LUNCH'][type.key][dayIdx - 1].length === 0"
                class="add-btn group-icon"
                @click.prevent="handleCellClick('LUNCH', type.key, dayIdx)"
              >
                <i class="el-icon-circle-plus" />
                <span class="add-text">ADD</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Dinner Section -->
        <div class="meal-section relative">
          <div class="meal-indicator dinner-indicator">
            <span class="rotated-text text-tertiary font-extrabold">晚餐</span>
          </div>

          <div v-for="type in dishTypes" :key="'dinner-' + type.key" class="meal-grid border-b">
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
                  v-for="dish in matrix['DINNER'][type.key][dayIdx - 1]"
                  :key="dish.id"
                  class="dish-card dinner-dish group"
                  @click="handleUpdate(dish)"
                >
                  <p class="dish-name">{{ dish.name }}</p>
                  <i class="el-icon-edit edit-icon" />
                </div>
              </div>

              <button
                v-if="matrix['DINNER'][type.key][dayIdx - 1].length === 0"
                class="add-btn group-icon"
                @click.prevent="handleCellClick('DINNER', type.key, dayIdx)"
              >
                <i class="el-icon-circle-plus" />
                <span class="add-text">ADD</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Global Floating Add Button -->
    <button class="fab-btn editorial-gradient shadow-2xl transition-transform transform active:scale-95 hover:scale-110" @click="handleAddGlobal">
      <i class="el-icon-plus text-white font-bold text-2xl leading-none" />
    </button>

    <!-- 新增/编辑弹窗 -->
    <dish-form ref="dishForm" @refresh="getList" />
  </div>
</template>

<script>
import { queryDishes } from '@/api/dish'
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
      dishList: [],
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
        { key: 'SOUP', cn: '汤', en: 'Soup' },
        { key: 'MAIN', cn: '主菜', en: 'Main' },
        { key: 'SIDE', cn: '副菜', en: 'Side' },
        { key: 'VEGETABLE', cn: '素菜', en: 'Veg' },
        { key: 'RICE', cn: '米饭', en: 'Rice' }
      ]
    }
  },
  computed: {
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

      this.dishList.forEach(dish => {
        if (!dish.schedule || !dish.mealTypes) return
        dish.schedule.forEach(sched => {
          // split format like "1-1" or "1-7"
          const parts = sched.split('-')
          if (parts.length === 2 && parts[0] === this.currentWeek) {
            const day = parseInt(parts[1]) - 1 // 0 to 6
            if (day >= 0 && day <= 6 && dish.dishType) {
              dish.mealTypes.forEach(mt => {
                if (layout[mt] && layout[mt][dish.dishType]) {
                  layout[mt][dish.dishType][day].push(dish)
                }
              })
            }
          }
        })
      })
      return layout
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      // Fetch maximum available dishes to construct the board natively
      queryDishes({ page: 0, size: 9999 }).then(response => {
        this.dishList = response.content || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    handleUpdate(dish) {
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleUpdate(dish)
      }
    },
    handleCellClick(mealType, dishType, day) {
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleAdd({
          week: this.currentWeek,
          day: day,
          mealType: mealType,
          dishType: dishType
        })
      }
    },
    handleAddGlobal() {
      if (this.$refs.dishForm) {
        this.$refs.dishForm.handleAdd()
      }
    },
    handlePrint() {
      this.$message.info('导出/打印功能正在开发中...')
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
</style>

