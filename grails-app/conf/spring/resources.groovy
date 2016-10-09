// Place your Spring DSL code here
beans = {
    injectorService(redminePlanInjector.InjectorService) {}
    injectorJob(redminePlanInjector.InjectorJob) {
        injectorService = ref("injectorService")
        //logService = ref("logService")
    }
}
