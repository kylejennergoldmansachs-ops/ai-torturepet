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

    return true;
}

void Reservoir::apply_inputs(const std::vector<float>& input_vector) {
    if (input_vector.empty()) return;
    for (size_t i = 0; i < input_vector.size(); ++i) {
        uint32_t idx = (uint32_t)((i * 2654435761u) % neurons_);
        potentials_[idx] += input_vector[i];
    }
}

void Reservoir::step() {
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
                potentials_[tgt] += w;
            }
        }
    }
}

std::string Reservoir::export_state_summary() {
    std::ostringstream ss;
    size_t sample = std::max<uint32_t>(1, uint32_t(neurons_ / 8));
    float valence = 0.0f;
    uint32_t spike_sum = 0;
    for (uint32_t i = 0; i < neurons_; ++i) {
        spike_sum += spikes_[i];
        if (i < sample) valence += spikes_[i];
    }
    float arousal = float(spike_sum) / float(neurons_ + 1);
    valence = valence / float(sample + 1);

    ss << "{"valence":" << std::fixed << std::setprecision(3) << valence
       << ","arousal":" << arousal << ","top_anchors":[]}";
    return ss.str();
}

std::vector<float> Reservoir::get_readout_vector() {
    const int readout_dim = 8;
    std::vector<float> out(readout_dim, 0.0f);
    uint32_t window = std::max<uint32_t>(1, uint32_t(neurons_ / readout_dim));
    for (int d = 0; d < readout_dim; ++d) {
        uint32_t start = d * window;
        uint32_t end = (d == readout_dim - 1) ? neurons_ : (start + window);
        uint32_t sum = 0;
        for (uint32_t i = start; i < end; ++i) sum += spikes_[i];
        out[d] = float(sum) / float(window + 1);
    }
    return out;
}

int Reservoir::create_word_cluster(const std::string &label, uint32_t cluster_size) {
    static int next_id = 1;
    (void)label; (void)cluster_size;
    return next_id++;
}
