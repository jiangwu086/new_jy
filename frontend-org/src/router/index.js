import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { guest: true }
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '',          redirect: '/dashboard' },
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/Dashboard.vue') },
      { path: 'locations', name: 'Locations', component: () => import('@/views/location/LocationList.vue') },
      { path: 'checkins',  name: 'Checkins',  component: () => import('@/views/checkin/CheckinList.vue') },
      { path: 'workers',   name: 'Workers',   component: () => import('@/views/worker/WorkerList.vue') },
      { path: 'gifts',     name: 'Gifts',     component: () => import('@/views/gift/GiftList.vue') },
      { path: 'review',    name: 'Review',    component: () => import('@/views/review/ReviewView.vue') },
      { path: 'checkin-config', name: 'CheckinConfig',
        component: () => import('@/views/checkin-config/CheckinConfigView.vue') },
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('orgToken')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    next('/')
  } else {
    next()
  }
})

export default router
