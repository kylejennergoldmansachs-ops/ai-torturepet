// native-brain/src/reservoir.cpp
#include "reservoir.h"
#include <random>
#include <sstream>
#include <iomanip>
#include <algorithm>

using namespace brain;

Reservoir::~Reservoir() {
}

bool Reservoir::init(const Config &cfg, uint32_t seed) {
    neurons_ = cfg.neuron_count;
    fanout_ = cfg.fanout;
    dt_ms_ = cfg.dt_ms;

    // Basic safety: cap neurons to avoid accidental 0
    if (neurons_ == 0) neurons_ = 1;
    if (fanout_ == 0) fanout_ = 1;

    potentials_.assign(neurons_, 0.0f);
    spikes_.assign(neurons_, 0);

    out_index_ptr_.assign(neurons_ + 1, 0);
    std::mt19937 rng(seed);
    std::uniform_int_distribution<uint32_t> target_dist(0, neurons_ - 1);
    std::normal_distribution<float> weight_dist(0.0f, 0.08f);

    out_targets_.reserve(size_t(neurons_) * fanout_);
    out_weights_.reserve(size_t(neurons_) * fanout_);

    for (uint32_t i = 0; i < neurons_; ++i) {
        out_index_ptr_[i] = static_cast<uint32_t>(out_targets_.size());
        for (uint32_t f = 0; f < fanout_; ++f) {
            uint32_t t = target_dist(rng);
            out_targets_.push_back(t);
            out_weights_.push_back(weight_dist(rng));
        }
    }
    out_index_ptr_[neurons_] = static_cast<uint32_t>(out_targets_.size());

    size_t readout_dim = 8;
    readout_weights_.assign(size_t(neurons_) * readout_dim, 0.001f);

    // initialize cluster bookkeeping (empty)
    cluster_labels_.clear();
    cluster_sizes_.clear();

    return true;
}

void Reservoir::apply_inputs(const std::vector<float>& input_vector) {
    if (input_vector.empty() || neurons_ == 0) return;
    // Simple hash mapping from input index -> neuron index
    for (size_t i = 0; i < input_vector.size(); ++i) {
        uint32_t idx = static_cast<uint32_t>((i * 2654435761u) % neurons_);
        potentials_[idx] += input_vector[i];
    }
}

void Reservoir::step() {
    if (neurons_ == 0) return;

    const float leak = 0.92f;
    const float threshold = 1.0f;

    std::fill(spikes_.begin(), spikes_.end(), 0);

    for (uint32_t n = 0; n < neurons_; ++n) {
        potentials_[n] *= leak;
        if (potentials_[n] >= threshold) {
            spikes_[n] = 1;
            potentials_[n] = 0.0f;
            uint32_t start = out_index_ptr_[n];
            uint32_t end = out_index_ptr_[n + 1];
            for (uint32_t p = start; p < end; ++p) {
                uint32_t tgt = out_targets_[p];
                float w = out_weights_[p];
                // guard target index in rare cases
                if (tgt < neurons_) potentials_[tgt] += w;
            }
        }
    }
}

std::string Reservoir::export_state_summary() {
    // Build a compact JSON summary: { "valence": <float>, "arousal": <float>, "top_anchors": [] }
    if (neurons_ == 0) return R"({"valence":0.000,"arousal":0.000,"top_anchors":[]})";

    std::ostringstream ss;
    size_t sample = std::max<uint32_t>(1, static_cast<uint32_t>(neurons_ / 8));
    float valence = 0.0f;
    uint32_t spike_sum = 0;
    for (uint32_t i = 0; i < neurons_; ++i) {
        spike_sum += spikes_[i];
        if (i < sample) valence += spikes_[i];
    }
    float arousal = static_cast<float>(spike_sum) / static_cast<float>(neurons_ + 1);
    valence = valence / static_cast<float>(sample + 1);

    // Use raw fragments to safely include JSON punctuation and stream numbers.
    ss << R"({"valence":)" << std::fixed << std::setprecision(3) << valence
       << R"(,"arousal":)" << std::fixed << std::setprecision(6) << arousal
       << R"(,"top_anchors":[])})";
    return ss.str();
}

std::vector<float> Reservoir::get_readout_vector() {
    const int readout_dim = 8;
    std::vector<float> out(readout_dim, 0.0f);
    if (neurons_ == 0) return out;
    uint32_t window = std::max<uint32_t>(1, static_cast<uint32_t>(neurons_ / readout_dim));
    for (int d = 0; d < readout_dim; ++d) {
        uint32_t start = d * window;
        uint32_t end = (d == readout_dim - 1) ? neurons_ : (start + window);
        uint32_t sum = 0;
        for (uint32_t i = start; i < end; ++i) sum += spikes_[i];
        out[d] = static_cast<float>(sum) / static_cast<float>(window + 1);
    }
    return out;
}

int Reservoir::create_word_cluster(const std::string &label, uint32_t cluster_size) {
    // Simple in-memory registry for clusters. Returns a 1-based ID.
    cluster_labels_.push_back(label);
    cluster_sizes_.push_back(cluster_size);
    return static_cast<int>(cluster_labels_.size()); // 1-based id
}
