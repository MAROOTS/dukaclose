import axios from 'axios'

const api = axios.create({
    baseURL: '/api/v1',
    withCredentials: true,
    // axios reads the XSRF-TOKEN cookie and sends X-XSRF-TOKEN by default,
    // which is exactly what Spring Security expects.
})

// AuthContext registers a function here that clears the logged-in user.
let unauthorizedHandler = null
export function setUnauthorizedHandler(fn) {
    unauthorizedHandler = fn
}

api.interceptors.response.use(
    (response) => response,
    (error) => {
        const url = error.config?.url ?? ''
        const isAuthProbe = url.includes('/auth/login') || url.endsWith('/me')
        if (error.response?.status === 401 && !isAuthProbe) {
            unauthorizedHandler?.() // session expired: back to the login screen
        }
        return Promise.reject(error)
    },
)

/** Turn any axios error into a message a shop attendant can read. */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
    if (!error.response) {
        return 'Cannot reach the server. Check your internet connection. Nothing was saved.'
    }
    return error.response.data?.message ?? fallback
}

export default api