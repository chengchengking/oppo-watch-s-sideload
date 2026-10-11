export default {
  onCreate() {
    console.info('Application onCreate')
  },
  onShow() {
    console.info('Application onShow')
  },
  onHide() {
    console.info('Application onHide')
  },
  onDestroy() {
    console.info('Application onDestroy')
  },
  onError() {
    console.log('Application onError')
  },
  onPageNotFound(params) {
    const { uri = '' } = params
    console.error('error uri', uri)
  }
}
