try {
    const proto = Object.getPrototypeOf(navigator);
    if (proto) {
        const desc = Object.getOwnPropertyDescriptor(proto, 'webdriver');
        if (desc) {
            if (desc.configurable) delete proto.webdriver;
            else Object.defineProperty(proto, 'webdriver', { get: () => undefined });
        }
    }
    if (Object.prototype.hasOwnProperty.call(navigator, 'webdriver')) {
        try { delete navigator.webdriver; } catch (e) {}
        try { Object.defineProperty(navigator, 'webdriver', { get: () => undefined }); } catch (e) {}
    }
} catch (e) {}
