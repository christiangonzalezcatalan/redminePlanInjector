// Place your Spring DSL code here
beans = {
    injectorService(redminePlanInjector.InjectorService) {}
    injectorConsumer(redminePlanInjector.InjectorConsumer) {
        injectorService = ref("injectorService")
    }
}
