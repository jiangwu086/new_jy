import http from '@/utils/request'

export const login          = (username, password) => http.post('/login', { username, password })
export const getInfo        = ()                   => http.get('/info')
export const getDashboard   = ()                   => http.get('/dashboard')

// 点位管理
export const getLocations   = ()        => http.get('/locations')
export const createLocation = data      => http.post('/locations', data)
export const updateLocation = (id, d)   => http.put(`/locations/${id}`, d)
export const deleteLocation = id        => http.delete(`/locations/${id}`)

// 打卡记录
export const getCheckins    = params    => http.get('/checkins', { params })

// 员工列表
export const getWorkers     = ()        => http.get('/workers')

// 礼品配置
export const listGifts      = ()        => http.get('/gifts')
export const createGift     = data      => http.post('/gifts', data)
export const updateGift     = (id, d)   => http.put(`/gifts/${id}`, d)
export const deleteGift     = id        => http.delete(`/gifts/${id}`)

// ─── 打卡审核 ────────────────────────────────────────────────
export const getPendingReviews = params     => http.get('/checkins/pending-review', { params })
export const reviewCheckin     = (id, data) => http.post(`/checkins/${id}/review`, data)

// ─── 打卡规则（机构级配置）────────────────────────────────────
export const getCheckinConfig    = ()    => http.get('/checkin-config')
export const updateCheckinConfig = data  => http.put('/checkin-config', data)
