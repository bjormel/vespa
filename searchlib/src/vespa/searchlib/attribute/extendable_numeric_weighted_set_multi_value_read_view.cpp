// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extendable_numeric_weighted_set_multi_value_read_view.h"

namespace search::attribute {

template <class MultiValueType, typename BaseType>
ExtendableNumericWeightedSetMultiValueReadView<MultiValueType, BaseType>::ExtendableNumericWeightedSetMultiValueReadView(const std::vector<BaseType>& data, const std::vector<uint32_t>& idx, const std::vector<int32_t>& weights)
    : attribute::IMultiValueReadView<MultiValueType>(),
      _data(data),
      _idx(idx),
      _weights(weights),
      _copy()
{
}

template <class MultiValueType, typename BaseType>
ExtendableNumericWeightedSetMultiValueReadView<MultiValueType, BaseType>::~ExtendableNumericWeightedSetMultiValueReadView() = default;

template <class MultiValueType, typename BaseType>
std::span<const MultiValueType>
ExtendableNumericWeightedSetMultiValueReadView<MultiValueType, BaseType>::get_values(uint32_t doc_id) const
{
    auto offset = _idx[doc_id];
    auto next_offset = _idx[doc_id + 1];
    std::span<const BaseType> raw(_data.data() + offset, next_offset - offset);
    if (_copy.size() < raw.size()) {
        _copy.resize(raw.size());
    }
    auto dst = _copy.data();
    auto* src_weight = _weights.data() + offset;
    for (auto &src : raw) {
        *dst = multivalue::ValueBuilder<MultiValueType>::build(src, *src_weight);
        ++src_weight;
        ++dst;
    }
    return std::span<const MultiValueType>(_copy.data(), raw.size());
}

template class ExtendableNumericWeightedSetMultiValueReadView<multivalue::WeightedValue<int64_t>, int64_t>;
template class ExtendableNumericWeightedSetMultiValueReadView<multivalue::WeightedValue<double>, double>;

}
