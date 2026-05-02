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
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/Dashboard.vue')  },
      { path: 'checkins',  name: 'Checkins',  component: () => import('@/views/checkin/CheckinList.vue')  },
      { path: 'locations', redirect: '/orgs' },   // 已并入机构管理，旧链接自动跳转
      { path: 'articles',  name: 'Articles',  component: () => import('@/views/article/ArticleList.vue')  },
      { path: 'users',     name: 'Users',     component: () => import('@/views/user/UserList.vue')        },
      { path: 'redeem',    name: 'Redeem',    component: () => import('@/views/redeem/RedeemView.vue')    },
      { path: 'config',    name: 'Config',    component: () => import('@/views/config/ConfigView.vue')    },
      { path: 'orgs',      name: 'Orgs',      component: () => import('@/views/org/OrgList.vue')           },
      { path: 'gifts',     name: 'Gifts',     component: () => import('@/views/gift/GiftList.vue')          },
      { path: 'review',    name: 'Review',    component: () => import('@/views/review/ReviewView.vue')      },
      { path: 'verify',    name: 'Verify',    component: () => import('@/views/verify/VerifyList.vue')       }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('adminToken')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.meta.guest && token) {
    next('/')
  } else {
    next()
  }
})

export default router
