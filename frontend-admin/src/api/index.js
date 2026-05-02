import http from '@/utils/request'

// ─── 认证 ────────────────────────────────────────────────────
export const login = (username, password) =>
  http.post('/login', { username, password })

// ─── 系统配置 ────────────────────────────────────────────────
export const getConfigs    = ()          => http.get('/configs')
export const updateConfig  = (key, val)  => http.put(`/configs/${key}`, { value: val })

// ─── 点位管理 ────────────────────────────────────────────────
export const getLocations    = (orgId)  => http.get('/locations', { params: orgId != null ? { orgId } : {} })
export const getLocation     = id       => http.get(`/locations/${id}`)
export const createLocation  = data     => http.post('/locations', data)
export const updateLocation  = (id, d)  => http.put(`/locations/${id}`, d)
export const deleteLocation  = id       => http.delete(`/locations/${id}`)

// ─── 文章管理 ────────────────────────────────────────────────
export const getArticles     = params   => http.get('/articles', { params })
export const createArticle   = data     => http.post('/articles', data)
export const updateArticle   = (id, d)  => http.put(`/articles/${id}`, d)
export const deleteArticle   = id       => http.delete(`/articles/${id}`)

// ─── 用户管理 ────────────────────────────────────────────────
export const getUsers        = params   => http.get('/users', { params })
export const getUser         = id       => http.get(`/users/${id}`)
export const getUserCheckins = id       => http.get(`/users/${id}/checkins`)
export const adjustPoints    = (id, d)  => http.post(`/users/${id}/adjust-points`, d)

// ─── 积分核销 ────────────────────────────────────────────────
export const doRedeem        = data     => http.post('/redeem', data)
export const getRedeemLogs   = params   => http.get('/redeem/records', { params })
export const getAdminRedeems = params   => http.get('/redeems', { params })

// ─── 数据大屏 ────────────────────────────────────────────────
export const getDashboard   = ()        => http.get('/dashboard')

// ─── 打卡记录 ────────────────────────────────────────────────
export const getCheckins    = params    => http.get('/checkins', { params })

// ─── 机构管理 ────────────────────────────────────────────────
export const listOrgs         = ()          => http.get('/orgs')
export const createOrg        = data        => http.post('/orgs', data)
export const updateOrg        = (id, data)  => http.put(`/orgs/${id}`, data)
export const createOrgAdmin   = (orgId, d)  => http.post(`/orgs/${orgId}/admins`, d)

// ─── 礼品管理 ────────────────────────────────────────────────
export const listGifts        = ()          => http.get('/gifts')
export const createGift       = data        => http.post('/gifts', data)
export const updateGift       = (id, data)  => http.put(`/gifts/${id}`, data)
export const deleteGift       = id          => http.delete(`/gifts/${id}`)

// ─── 打卡审核 ────────────────────────────────────────────────
export const getPendingReviews = params      => http.get('/checkins/pending-review', { params })
export const reviewCheckin     = (id, data)  => http.post(`/checkins/${id}/review`, data)
