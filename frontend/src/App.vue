<script setup>
import { computed, onMounted, ref } from "vue";

const API_BASE_URL = "http://localhost:8080/api";
const API_URL = "http://localhost:8080/api/customers";
const AUTH_URL = `${API_BASE_URL}/auth`;

const checkingSession = ref(true);
const authenticated = ref(false);
const currentUsername = ref("");
const loggingIn = ref(false);
const loggingOut = ref(false);
const loginError = ref("");

const loginForm = ref({
  username: "admin",
  password: "",
});

const customers = ref([]);
const loading = ref(false);
const submitting = ref(false);
const deletingId = ref(null);
const editingId = ref(null);
const showForm = ref(false);

const searchKeyword = ref("");
const statusFilter = ref("");
const appliedKeyword = ref("");
const appliedStatus = ref("");

const currentPage = ref(0);
const pageSize = ref(10);
const totalElements = ref(0);
const totalPages = ref(0);
const firstPage = ref(true);
const lastPage = ref(true);

const errorMessage = ref("");
const formMessage = ref("");
const successMessage = ref("");

const form = ref({
  name: "",
  phone: "",
  email: "",
  status: "POTENTIAL",
});

const hasAppliedFilters = computed(() => {
  return appliedKeyword.value !== "" || appliedStatus.value !== "";
});

const displayedPage = computed(() => {
  if (totalPages.value === 0) {
    return 0;
  }

  return currentPage.value + 1;
});

function clearCustomerState() {
  customers.value = [];
  currentPage.value = 0;
  totalElements.value = 0;
  totalPages.value = 0;
  firstPage.value = true;
  lastPage.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  closeForm();
}

function expireSession(message = "登录状态已失效，请重新登录") {
  authenticated.value = false;
  currentUsername.value = "";
  loginForm.value.password = "";
  loginError.value = message;
  clearCustomerState();
}

async function checkSession() {
  checkingSession.value = true;
  loginError.value = "";

  try {
    const response = await fetch(`${AUTH_URL}/me`, {
      method: "GET",
      credentials: "include",
    });

    const responseData = await response.json().catch(() => null);

    if (response.status === 401) {
      authenticated.value = false;
      return;
    }

    if (!response.ok) {
      throw new Error(responseData?.message || `HTTP ${response.status}`);
    }

    authenticated.value = true;
    currentUsername.value = responseData.username;
    await loadCustomers(0);
  } catch (error) {
    authenticated.value = false;
    loginError.value = `无法检查登录状态：${error.message}`;
  } finally {
    checkingSession.value = false;
  }
}

async function login() {
  loginError.value = "";

  if (!loginForm.value.username.trim()) {
    loginError.value = "请输入用户名";
    return;
  }

  if (!loginForm.value.password) {
    loginError.value = "请输入密码";
    return;
  }

  loggingIn.value = true;

  try {
    const response = await fetch(`${AUTH_URL}/login`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username: loginForm.value.username.trim(),
        password: loginForm.value.password,
      }),
    });

    const responseData = await response.json().catch(() => null);

    if (!response.ok) {
      throw new Error(responseData?.message || `HTTP ${response.status}`);
    }

    authenticated.value = true;
    currentUsername.value = responseData.username;
    loginForm.value.password = "";
    await loadCustomers(0);
  } catch (error) {
    loginForm.value.password = "";
    loginError.value = `登录失败：${error.message}`;
  } finally {
    loggingIn.value = false;
  }
}

async function logout() {
  loggingOut.value = true;
  errorMessage.value = "";

  try {
    const response = await fetch(`${AUTH_URL}/logout`, {
      method: "POST",
      credentials: "include",
    });

    const responseData = await response.json().catch(() => null);

    if (!response.ok && response.status !== 401) {
      throw new Error(responseData?.message || `HTTP ${response.status}`);
    }

    expireSession("");
  } catch (error) {
    errorMessage.value = `退出失败：${error.message}`;
  } finally {
    loggingOut.value = false;
  }
}

async function loadCustomers(page = currentPage.value) {
  loading.value = true;
  errorMessage.value = "";

  const params = new URLSearchParams({
    page: String(page),
    size: String(pageSize.value),
    keyword: appliedKeyword.value,
    status: appliedStatus.value,
  });

  try {
    const response = await fetch(`${API_URL}?${params}`, {
      credentials: "include",
    });

    const responseData = await response.json().catch(() => null);

    if (response.status === 401) {
      expireSession();
      return;
    }

    if (!response.ok) {
      throw new Error(responseData?.message || `HTTP ${response.status}`);
    }

    if (!Array.isArray(responseData?.content)) {
      throw new Error("后端分页响应格式不正确");
    }

    customers.value = responseData.content;
    currentPage.value = responseData.page;
    pageSize.value = responseData.size;
    totalElements.value = responseData.totalElements;
    totalPages.value = responseData.totalPages;
    firstPage.value = responseData.first;
    lastPage.value = responseData.last;
  } catch (error) {
    customers.value = [];
    totalElements.value = 0;
    totalPages.value = 0;
    firstPage.value = true;
    lastPage.value = true;
    errorMessage.value = `客户数据加载失败：${error.message}`;
  } finally {
    loading.value = false;
  }
}

async function applyFilters() {
  appliedKeyword.value = searchKeyword.value.trim();
  appliedStatus.value = statusFilter.value;
  await loadCustomers(0);
}

async function clearFilters() {
  searchKeyword.value = "";
  statusFilter.value = "";
  appliedKeyword.value = "";
  appliedStatus.value = "";
  await loadCustomers(0);
}

async function changePage(page) {
  if (loading.value || page < 0 || page >= totalPages.value) {
    return;
  }

  await loadCustomers(page);
}

async function changePageSize() {
  await loadCustomers(0);
}

async function saveCustomer() {
  formMessage.value = "";
  successMessage.value = "";

  if (!form.value.name.trim()) {
    formMessage.value = "请输入客户姓名";
    return;
  }

  submitting.value = true;

  const isEditing = editingId.value !== null;

  const requestUrl = isEditing ? `${API_URL}/${editingId.value}` : API_URL;

  const requestMethod = isEditing ? "PUT" : "POST";

  try {
    const response = await fetch(requestUrl, {
      method: requestMethod,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        name: form.value.name.trim(),
        phone: form.value.phone.trim(),
        email: form.value.email.trim(),
        status: form.value.status,
      }),
    });

    const responseData = await response.json().catch(() => null);

    if (response.status === 401) {
      expireSession();
      return;
    }

    if (!response.ok) {
      const firstFieldError = Object.values(responseData?.errors ?? {})[0];

      throw new Error(
        firstFieldError || responseData?.message || `HTTP ${response.status}`,
      );
    }

    successMessage.value = isEditing ? "客户修改成功" : "客户添加成功";

    const pageAfterSave = isEditing ? currentPage.value : 0;

    closeForm();
    await loadCustomers(pageAfterSave);
  } catch (error) {
    formMessage.value = `${
      isEditing ? "客户修改失败" : "客户添加失败"
    }：${error.message}`;
  } finally {
    submitting.value = false;
  }
}

function openCreateForm() {
  successMessage.value = "";
  editingId.value = null;
  resetForm();
  showForm.value = true;
}

function startEdit(customer) {
  successMessage.value = "";
  formMessage.value = "";
  editingId.value = customer.id;

  form.value = {
    name: customer.name ?? "",
    phone: customer.phone ?? "",
    email: customer.email ?? "",
    status: customer.status ?? "POTENTIAL",
  };

  showForm.value = true;

  window.scrollTo({
    top: 0,
    behavior: "smooth",
  });
}

async function deleteCustomer(customer) {
  const confirmed = window.confirm(
    `确定删除客户“${customer.name}”吗？删除后无法恢复。`,
  );

  if (!confirmed) {
    return;
  }

  deletingId.value = customer.id;
  errorMessage.value = "";
  successMessage.value = "";

  try {
    const response = await fetch(`${API_URL}/${customer.id}`, {
      method: "DELETE",
      credentials: "include",
    });

    if (response.status === 401) {
      expireSession();
      return;
    }

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    if (editingId.value === customer.id) {
      closeForm();
    }

    const pageAfterDelete =
      customers.value.length === 1 && currentPage.value > 0
        ? currentPage.value - 1
        : currentPage.value;

    successMessage.value = "客户删除成功";
    await loadCustomers(pageAfterDelete);
  } catch (error) {
    errorMessage.value = `客户删除失败：${error.message}`;
  } finally {
    deletingId.value = null;
  }
}

function resetForm() {
  form.value = {
    name: "",
    phone: "",
    email: "",
    status: "POTENTIAL",
  };

  formMessage.value = "";
}

function closeForm() {
  resetForm();
  editingId.value = null;
  showForm.value = false;
}

function formatStatus(status) {
  if (status === "ACTIVE") {
    return "正式客户";
  }

  if (status === "POTENTIAL") {
    return "潜在客户";
  }

  return status;
}

function formatTime(time) {
  if (!time) {
    return "-";
  }

  return new Date(time).toLocaleString();
}

onMounted(checkSession);
</script>

<template>
  <main class="page">
    <section v-if="checkingSession" class="panel session-panel">
      <div class="loading-spinner" aria-hidden="true"></div>

      <h1>正在检查登录状态</h1>

      <p>请稍候……</p>
    </section>

    <section v-else-if="!authenticated" class="panel login-panel">
      <div class="login-header">
        <span class="login-badge">NH</span>

        <div>
          <h1>管理员登录</h1>
          <p>登录后进入客户管理系统</p>
        </div>
      </div>

      <form class="login-form" @submit.prevent="login">
        <label>
          <span>用户名</span>

          <input
            v-model="loginForm.username"
            type="text"
            autocomplete="username"
            :disabled="loggingIn"
            placeholder="请输入管理员用户名"
          />
        </label>

        <label>
          <span>密码</span>

          <input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            :disabled="loggingIn"
            placeholder="请输入管理员密码"
          />
        </label>

        <p v-if="loginError" class="login-error">
          {{ loginError }}
        </p>

        <button
          type="submit"
          class="primary-button login-button"
          :disabled="loggingIn"
        >
          {{ loggingIn ? "正在登录……" : "登录" }}
        </button>
      </form>
    </section>

    <section v-else class="panel">
      <div class="page-header">
        <div>
          <h1>客户管理系统</h1>
          <p>管理客户资料和客户状态</p>

          <p class="current-user">
            当前用户：{{ currentUsername }}
          </p>
        </div>

        <div class="header-actions">
          <button
            class="secondary-button"
            :disabled="loading"
            @click="loadCustomers(currentPage)"
          >
            {{ loading ? "刷新中……" : "刷新列表" }}
          </button>

          <button class="primary-button" @click="openCreateForm">
            新增客户
          </button>

          <button
            class="logout-button"
            :disabled="loggingOut"
            @click="logout"
          >
            {{ loggingOut ? "退出中……" : "退出登录" }}
          </button>
        </div>
      </div>

      <p v-if="successMessage" class="success-message">
        {{ successMessage }}
      </p>

      <form
        v-if="showForm"
        class="customer-form"
        novalidate
        @submit.prevent="saveCustomer"
      >
        <h2>
          {{ editingId !== null ? "编辑客户" : "新增客户" }}
        </h2>

        <div class="form-grid">
          <label>
            <span>客户姓名 *</span>

            <input v-model="form.name" type="text" placeholder="例如：张三" />
          </label>

          <label>
            <span>电话号码</span>

            <input
              v-model="form.phone"
              type="text"
              placeholder="例如：13800138000"
            />
          </label>

          <label>
            <span>电子邮箱</span>

            <input
              v-model="form.email"
              type="email"
              placeholder="支持QQ、谷歌、微软、163等邮箱"
            />
          </label>

          <label>
            <span>客户状态</span>

            <select v-model="form.status">
              <option value="POTENTIAL">潜在客户</option>

              <option value="ACTIVE">正式客户</option>
            </select>
          </label>
        </div>

        <p v-if="formMessage" class="form-error">
          {{ formMessage }}
        </p>

        <div class="form-actions">
          <button type="button" class="cancel-button" @click="closeForm">
            取消
          </button>

          <button type="submit" class="primary-button" :disabled="submitting">
            {{
              submitting
                ? "正在保存……"
                : editingId !== null
                  ? "保存修改"
                  : "保存客户"
            }}
          </button>
        </div>
      </form>

      <div v-if="!loading && !errorMessage" class="filter-bar">
        <input
          v-model="searchKeyword"
          class="search-input"
          type="text"
          placeholder="输入客户姓名搜索"
          @keyup.enter="applyFilters"
        />

        <select v-model="statusFilter" class="filter-select">
          <option value="">全部状态</option>

          <option value="POTENTIAL">潜在客户</option>

          <option value="ACTIVE">正式客户</option>
        </select>

        <button type="button" class="search-button" @click="applyFilters">
          搜索
        </button>

        <button
          v-if="searchKeyword || statusFilter || hasAppliedFilters"
          type="button"
          class="clear-filter-button"
          @click="clearFilters"
        >
          清空筛选
        </button>

        <span class="result-count"> 共 {{ totalElements }} 条 </span>
      </div>

      <p v-if="loading" class="state-message">正在加载客户数据……</p>

      <p v-else-if="errorMessage" class="error-message">
        {{ errorMessage }}
      </p>

      <div
        v-else-if="customers.length === 0 && !hasAppliedFilters"
        class="empty-state"
      >
        <h2>暂无客户数据</h2>

        <p>点击右上角“新增客户”创建第一条客户记录。</p>
      </div>

      <div v-else-if="customers.length > 0" class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>姓名</th>
              <th>电话</th>
              <th>邮箱</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>

          <tbody>
            <tr v-for="customer in customers" :key="customer.id">
              <td>{{ customer.id }}</td>

              <td>{{ customer.name }}</td>

              <td>
                {{ customer.phone || "-" }}
              </td>

              <td>
                {{ customer.email || "-" }}
              </td>

              <td>
                <span class="status" :class="customer.status?.toLowerCase()">
                  {{ formatStatus(customer.status) }}
                </span>
              </td>

              <td>
                {{ formatTime(customer.createdAt) }}
              </td>

              <td>
                <div class="row-actions">
                  <button class="edit-button" @click="startEdit(customer)">
                    编辑
                  </button>

                  <button
                    class="delete-button"
                    :disabled="deletingId === customer.id"
                    @click="deleteCustomer(customer)"
                  >
                    {{ deletingId === customer.id ? "删除中……" : "删除" }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="pagination">
          <button
            type="button"
            class="secondary-button"
            :disabled="firstPage || loading"
            @click="changePage(currentPage - 1)"
          >
            上一页
          </button>

          <span class="page-information">
            第 {{ displayedPage }} 页，共 {{ totalPages }} 页
          </span>

          <button
            type="button"
            class="secondary-button"
            :disabled="lastPage || loading"
            @click="changePage(currentPage + 1)"
          >
            下一页
          </button>

          <label class="page-size-control">
            <span>每页显示</span>

            <select
              v-model.number="pageSize"
              :disabled="loading"
              @change="changePageSize"
            >
              <option :value="5">5 条</option>
              <option :value="10">10 条</option>
              <option :value="20">20 条</option>
            </select>
          </label>
        </div>
      </div>

      <div v-else class="empty-state">
        <h2>没有符合条件的客户</h2>

        <p>请修改姓名或客户状态筛选条件。</p>

        <button type="button" class="secondary-button" @click="clearFilters">
          清空筛选条件
        </button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100vh;
  padding: 48px 24px;
  background: #f4f7fb;
}

.panel {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(30, 41, 59, 0.08);
}

.session-panel,
.login-panel {
  max-width: 460px;
}

.session-panel {
  display: flex;
  min-height: 240px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.session-panel h1 {
  margin-top: 18px;
}

.session-panel p {
  margin-bottom: 0;
  color: #667085;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 4px solid #dbeafe;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.login-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.login-header h1 {
  margin-bottom: 6px;
}

.login-header p {
  margin: 0;
  color: #667085;
}

.login-badge {
  display: flex;
  width: 54px;
  height: 54px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: #2563eb;
  border-radius: 14px;
  font-size: 20px;
  font-weight: 700;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.login-form input {
  box-sizing: border-box;
  width: 100%;
}

.login-error {
  margin: 0;
  padding: 14px;
  color: #b42318;
  background: #fef3f2;
  border-radius: 8px;
  text-align: center;
}

.login-button {
  width: 100%;
  margin-top: 2px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: center;
  margin-bottom: 32px;
}

h1 {
  margin: 0 0 8px;
  color: #172033;
  font-size: 30px;
}

.page-header p {
  margin: 0;
  color: #667085;
}

.page-header .current-user {
  margin-top: 8px;
  color: #175cd3;
  font-size: 14px;
}

.header-actions,
.form-actions,
.row-actions {
  display: flex;
  gap: 10px;
}

button {
  padding: 10px 18px;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.primary-button {
  color: #ffffff;
  background: #2563eb;
}

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button,
.cancel-button {
  color: #344054;
  background: #e9eef5;
}

.secondary-button:hover:not(:disabled),
.cancel-button:hover:not(:disabled) {
  background: #dfe5ed;
}

.edit-button {
  padding: 7px 12px;
  color: #175cd3;
  background: #eff8ff;
}

.edit-button:hover {
  background: #dbeeff;
}

.delete-button {
  padding: 7px 12px;
  color: #b42318;
  background: #fef3f2;
}

.delete-button:hover:not(:disabled) {
  background: #fee4e2;
}

.logout-button {
  color: #b42318;
  background: #fef3f2;
}

.logout-button:hover:not(:disabled) {
  background: #fee4e2;
}

.customer-form {
  margin-bottom: 28px;
  padding: 24px;
  background: #f8fafc;
  border: 1px solid #dbe3ee;
  border-radius: 12px;
}

.customer-form h2 {
  margin: 0 0 20px;
  color: #172033;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #344054;
  font-weight: 600;
}

input,
select {
  padding: 11px 12px;
  color: #172033;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  font: inherit;
}

input:focus,
select:focus {
  border-color: #2563eb;
  outline: 2px solid rgba(37, 99, 235, 0.12);
}

.form-actions {
  justify-content: flex-end;
  margin-top: 20px;
}

.state-message,
.error-message,
.success-message,
.form-error {
  padding: 16px;
  text-align: center;
  border-radius: 8px;
}

.error-message,
.form-error {
  color: #b42318;
  background: #fef3f2;
}

.success-message {
  margin-bottom: 20px;
  color: #067647;
  background: #ecfdf3;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.search-input {
  width: 260px;
}

.filter-select {
  min-width: 150px;
}

.search-button {
  color: #ffffff;
  background: #2563eb;
}

.search-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.clear-filter-button {
  color: #475467;
  background: #e9eef5;
}

.clear-filter-button:hover {
  background: #dfe5ed;
}

.result-count {
  margin-left: auto;
  color: #667085;
  font-size: 14px;
}

.empty-state {
  padding: 64px 20px;
  text-align: center;
  color: #667085;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 12px;
}

.empty-state h2 {
  margin-top: 0;
  color: #344054;
}

.table-wrapper {
  overflow-x: auto;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-width: 720px;
  padding-top: 22px;
}

.page-information {
  min-width: 150px;
  color: #475467;
  text-align: center;
}

.page-size-control {
  flex-direction: row;
  align-items: center;
  margin-left: 12px;
  font-size: 14px;
  font-weight: 400;
}

.page-size-control select {
  padding: 8px 10px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 14px 12px;
  text-align: left;
  border-bottom: 1px solid #e5e7eb;
}

th {
  color: #475467;
  background: #f8fafc;
}

.status {
  display: inline-block;
  padding: 4px 10px;
  color: #175cd3;
  background: #eff8ff;
  border-radius: 999px;
}

.status.active {
  color: #067647;
  background: #ecfdf3;
}

@media (max-width: 700px) {
  .page {
    padding: 24px 12px;
  }

  .panel {
    padding: 20px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    flex-wrap: wrap;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .search-input,
  .filter-select,
  .search-button,
  .clear-filter-button {
    box-sizing: border-box;
    width: 100%;
  }

  .result-count {
    margin-left: 0;
  }

  .pagination {
    justify-content: flex-start;
  }
}
</style>
