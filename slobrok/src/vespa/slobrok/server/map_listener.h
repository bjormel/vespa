// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "service_mapping.h"

namespace slobrok {

struct MapListener {
    virtual void add(const ServiceMapping &mapping) = 0;
    virtual void remove(const ServiceMapping &mapping) = 0;
    virtual void update(const ServiceMapping &old_mapping,
                        const ServiceMapping &new_mapping);
    virtual ~MapListener();
};

} // namespace slobrok

