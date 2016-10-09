package redminePlanInjector

class InjectorJob {
    static triggers = {
      simple repeatInterval: 20000l
    }

    def injectorService

    def execute() {
        injectorService.injectProcess()
    }
}
